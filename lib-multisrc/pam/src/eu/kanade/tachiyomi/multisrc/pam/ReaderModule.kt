package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import keiyoushi.network.get
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The reader's signer as shipped by the site. Each build renames the WASM exports and reshuffles
 * the tables of the imports that unmask the signer's secret, and sites rebuild every few days,
 * so all of it is read from the site's own bundle instead of being shipped with the extension.
 */
internal class ReaderModule(
    val module: WasmModule,
    /** Reader-side function name (`signAttestation`, `malloc`, ...) to export name. */
    val exports: Map<String, String>,
    /** The only host functions the signer may import, as module and name. */
    val resizeImport: Pair<String, String>,
    val unmaskImports: Map<Pair<String, String>, Unmask>,
) {
    fun export(name: String): String = exports[name] ?: throw IOException("Reader export $name missing")
}

/** Rewrites a 64-byte block in place: out[i] = (in[permutation[i]] ^ xor[i]) + add[i]. */
internal class Unmask(
    val memoryName: String,
    val function: String,
)

internal suspend fun OkHttpClient.fetchReaderModule(baseUrl: String, headers: Headers): ReaderModule {
    suspend fun asset(name: String): String = get("$baseUrl/build/assets/$name", headers).use { it.body.string() }

    val home = get(baseUrl, headers).use { it.body.string() }
    val entry = ENTRY_REGEX.find(home)?.groupValues?.get(1) ?: throw IOException("Reader entry script not found")
    val entryScript = asset(entry)
    val reader = READER_CHUNK_REGEX.findAll(entryScript).lastOrNull()?.groupValues
        ?: throw IOException("Reader chunk not found")

    // Vite lists every chunk a page loads, transitively, next to the page's loader.
    val entryDeps = MAP_DEPS_REGEX.find(entryScript)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] }.toList() }
        .orEmpty()
    val readerDeps = reader[2].split(',').filter(String::isNotEmpty).mapNotNull { entryDeps.getOrNull(it.toInt()) }

    // The export name map lives in a chunk shared by every reader version.
    val shared = (IMPORT_REGEX.findAll(asset(reader[1])).map { it.groupValues[1] } + readerDeps)
        .filter { it.endsWith(".js") && it != entry }
        .distinct()
        .toList()
        .firstNotNullOfOrNull { name -> asset(name).takeIf { "freeBuffer:\"" in it || "\"freeBuffer\",\"" in it } }
        ?: throw IOException("Reader signer bindings not found")
    val semantic = EXPORT_MAP_REGEX.find(shared)?.value
        ?.let { map -> PAIR_REGEX.findAll(map).associate { it.groupValues[1] to it.groupValues[2] } }
        ?: ARRAY_PAIR_REGEX.findAll(shared).associate { it.groupValues[1] to it.groupValues[2] }
    if ("freeBuffer" !in semantic || "signAttestation" !in semantic) {
        throw IOException("Reader export map not found")
    }

    val glue = MAP_DEPS_REGEX.find(shared)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] }.toList() }
        .orEmpty()
        .filter { it.endsWith(".js") }
        .firstNotNullOfOrNull { name -> asset(name).takeIf { WASM_PREFIX in it } }
        ?: throw IOException("Reader signer module not found")

    val glueNames = GLUE_EXPORT_REGEX.findAll(glue).associate { it.groupValues[1] to it.groupValues[2] }
    val exports = buildMap {
        semantic.forEach { (name, glueName) -> glueNames[glueName]?.let { put(name, it) } }
        glueNames["_malloc"]?.let { put("malloc", it) }
        CTORS_REGEX.find(glue)?.groupValues?.get(1)?.let { put("ctors", it) }
    }

    val (importObject, resizeName) = RESIZE_IMPORT_REGEX.find(glue)?.destructured
        ?: throw IOException("Unsupported reader signer build")
    val importModule = Regex("""var [\w$]+=\{([\w$]+):${Regex.escape(importObject)}\}""").find(glue)?.groupValues?.get(1)
        ?: throw IOException("Unsupported reader signer build")

    // The site's import functions now also use rotation, carry and multiple rounds.
    // Keep their JavaScript instead of assuming a particular table arrangement.
    val unmasks = UNMASK_FUNCTION_REGEX.findAll(glue).mapNotNull { match ->
        val functionStart = glue.indexOf("function", match.range.first)
        val bodyStart = glue.indexOf('{', functionStart)
        var depth = 1
        var cursor = bodyStart + 1
        while (cursor < glue.length && depth > 0) {
            when (glue[cursor++]) {
                '{' -> depth++
                '}' -> depth--
            }
        }
        if (depth != 0) throw IOException("Reader import function is incomplete")
        val function = glue.substring(functionStart, cursor)
        val pointer = Regex.escape(match.groupValues[2])
        val memory = Regex("""([\w$]+)\.slice\($pointer,$pointer\+64\)""")
            .find(function)?.groupValues?.get(1) ?: return@mapNotNull null
        (importModule to match.groupValues[1]) to Unmask(memory, function)
    }.toMap()
    if (unmasks.isEmpty()) throw IOException("Unsupported reader signer build")

    val wasm = WASM_REGEX.find(glue)?.groupValues?.get(1) ?: throw IOException("Reader signer module not found")

    return ReaderModule(
        module = Parser.parse(Base64.decode(wasm, Base64.DEFAULT)),
        exports = exports,
        resizeImport = importModule to resizeName,
        unmaskImports = unmasks,
    )
}

internal const val UNMASK_SIZE = 64
private const val WASM_PREFIX = "\"AGFzbQ"

private val ENTRY_REGEX = Regex("""<script[^>]+src="(?:https?://[^/"]+)?/build/assets/([^"]+\.js)"""")
private val READER_CHUNK_REGEX = Regex(
    """"\./pages/(?:[\w-]+/)*serie-chapter-reader\.tsx":\(\)=>[\w$]+\(\(\)=>import\("\./([^"]+\.js)"\)(?:\.then\([^)]*\))?(?:,__vite__mapDeps\(\[([\d,]*)\]\))?""",
)
private val IMPORT_REGEX = Regex("""from"\./([^"]+\.js)"""")
private val EXPORT_MAP_REGEX = Regex("""\{freeBuffer:"[^}]+\}""")
private val PAIR_REGEX = Regex("""([\w$]+):"(_[\w$]+)"""")
private val MAP_DEPS_REGEX = Regex("""m\.f=\[([^\]]+)\]""")
private val DEP_REGEX = Regex(""""assets/([^"]+)"""")
private val GLUE_EXPORT_REGEX = Regex("""[\w$]+\.(_[\w$]+)=[\w$]+\.([\w$]+)""")
private val CTORS_REGEX = Regex("""=!0,[\w$]+\.([\w$]+)\(\),null==""")

// emscripten_resize_heap, the first entry of the glue's import object.
private val RESIZE_IMPORT_REGEX = Regex(
    """([\w$]+)=\{([\w$]+):[\w$]+=>\{var [\w$]+=[\w$]+\.length;if\(\d+<\([\w$]+>>>=0\)\)return!1""",
)

private val UNMASK_FUNCTION_REGEX = Regex("""([\w$]+):function\(([\w$]+)\)\{""")
private val WASM_REGEX = Regex(""""(AGFzbQ[A-Za-z0-9+/=]+)"""")

private val ARRAY_PAIR_REGEX = Regex("""\["([\w$]+)","(_[\w$]+)"\]""")

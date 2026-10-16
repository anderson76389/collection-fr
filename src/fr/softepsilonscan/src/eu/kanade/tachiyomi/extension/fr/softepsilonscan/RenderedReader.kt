package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class RenderedReader {
    private val mutex = Mutex()
    private val cache get() = File(applicationContext.cacheDir, "soft-epsilon-rendered")
    private val script = javaClass.getResource("/assets/rendered_page.js")!!.readText()

    fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != CACHE_HOST) return chain.proceed(request)
        val parts = request.url.pathSegments
        val valid = parts.size == 2 && SESSION.matches(parts[0]) && FILE.matches(parts[1])
        val file = if (valid) File(File(cache, parts[0]), parts[1]).takeIf(File::isFile) else null
        val body = if (file != null) {
            file.source().buffer().asResponseBody("image/png".toMediaType(), file.length())
        } else {
            ByteArray(0).toResponseBody("image/png".toMediaType())
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (file != null) 200 else 404)
            .message(if (file != null) "OK" else "Not Found")
            .body(body)
            .build()
    }

    suspend fun pages(url: String, headers: Headers): List<Page>? = mutex.withLock {
        val session = UUID.randomUUID().toString()
        val dir = File(cache, session)
        withContext(Dispatchers.IO) {
            cache.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24.hours.inWholeMilliseconds }
                ?.forEach { it.deleteRecursively() }
            if (!dir.mkdirs()) throw IOException("Impossible de créer le cache du lecteur Epsilon")
        }
        try {
            coroutineScope {
                val writer = this
                runWebView<List<Page>?>(timeout = 10.minutes) {
                    userAgent = headers["User-Agent"].orEmpty()
                    blockImages = false
                    var index = 0
                    var total: Int? = null
                    var pending = false
                    var progress = TimeSource.Monotonic.markNow()
                    // Polling leaves JavaScript prototypes, crypto and native bridge globals intact.
                    poll(500.milliseconds) {
                        if (progress.elapsedNow() > 45.seconds) {
                            reject(IOException("Epsilon : la page ${index + 1} n’a pas été rendue par le site. Vérifiez ce chapitre dans la WebView."))
                            return@poll
                        }
                        if (pending) return@poll
                        pending = true
                        evaluateJs("($script)($index)") { value ->
                            val result = value.parseAs<Snapshot>()
                            when (result.state) {
                                "legacy" -> resolve(null)
                                "error" -> reject(IOException("Epsilon : ${result.error}"))
                                "waiting" -> pending = false
                                "page" -> {
                                    if (result.index != index || result.total <= index || (total != null && total != result.total)) {
                                        reject(IOException("Epsilon : le nombre ou l’ordre des pages a changé pendant la lecture"))
                                        return@evaluateJs
                                    }
                                    total = result.total
                                    writer.launch(Dispatchers.IO) {
                                        try {
                                            val encoded = result.data?.takeIf { it.startsWith(PNG_PREFIX) }
                                                ?: throw IOException("Epsilon : image rendue absente")
                                            val bytes = Base64.decode(encoded.substring(PNG_PREFIX.length), Base64.DEFAULT)
                                            if (bytes.size < PNG_MAGIC.size || !bytes.copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC)) {
                                                throw IOException("Epsilon : image rendue invalide")
                                            }
                                            File(dir, "$index.png").writeBytes(bytes)
                                            withContext(Dispatchers.Main) {
                                                index++
                                                progress = TimeSource.Monotonic.markNow()
                                                pending = false
                                                if (index == total) resolve((0 until index).map { Page(it, imageUrl = "https://$CACHE_HOST/$session/$it.png") })
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            reject(e)
                                        }
                                    }
                                }
                                else -> reject(IOException("Epsilon : réponse inconnue du lecteur"))
                            }
                        }
                    }
                    onReceivedError { request, error ->
                        if (request.isForMainFrame) reject(IOException(error.description.toString()))
                    }
                    loadUrl(url)
                }
            }.also { if (it == null) withContext(Dispatchers.IO) { dir.deleteRecursively() } }
        } catch (e: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
            throw e
        }
    }

    @Serializable
    private class Snapshot(
        val state: String,
        val index: Int = -1,
        val total: Int = 0,
        val data: String? = null,
        val error: String? = null,
    )

    companion object {
        private const val CACHE_HOST = "soft-epsilon-rendered.local"
        private const val PNG_PREFIX = "data:image/png;base64,"
        private val SESSION = Regex("[a-f0-9-]{36}")
        private val FILE = Regex("[0-9]+\\.png")
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}

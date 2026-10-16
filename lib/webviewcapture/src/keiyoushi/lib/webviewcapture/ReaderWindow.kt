package keiyoushi.lib.webviewcapture

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
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
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Imports the visible, user-verified Mihon WebView into a private chapter cache.
 * [locator] must return { state: "ready", element, total } for a page, or a waiting/error state.
 * It must not modify browser prototypes, decrypt content or solve verification challenges.
 */
class ReaderWindow(private val sourceId: Long, private val locator: String) {
    private val mutex = Mutex()
    private val cache get() = File(applicationContext.cacheDir, "reader-window-$sourceId")
    private val geometry = javaClass.getResource("/assets/window_geometry.js")!!.readText()

    /** Serves only this source's cached PNGs; add before the source's rate limiter. */
    fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != HOST) return chain.proceed(request)
        val parts = request.url.pathSegments
        val valid = parts.size == 3 && parts[0] == sourceId.toString() && SESSION.matches(parts[1]) && FILE.matches(parts[2])
        val file = if (valid) File(File(cache, parts[1]), parts[2]).takeIf(File::isFile) else null
        val body = file?.let { it.source().buffer().asResponseBody("image/png".toMediaType(), it.length()) }
            ?: ByteArray(0).toResponseBody("image/png".toMediaType())
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (file == null) 404 else 200).message(if (file == null) "Not Found" else "OK").body(body).build()
    }

    /** Opens the ordinary Mihon WebView. The user starts import after the chapter is visible. */
    suspend fun pages(url: String): List<Page>? = mutex.withLock {
        val session = UUID.randomUUID().toString()
        val dir = File(cache, session)
        withContext(Dispatchers.IO) {
            cache.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24.hours.inWholeMilliseconds }
                ?.forEach { it.deleteRecursively() }
            if (!dir.mkdirs()) throw IOException("Impossible de créer le cache du chapitre")
        }
        try {
            withTimeout(10.minutes) {
                withContext(Dispatchers.Main.immediate) { importChapter(url, session, dir) }
                    .also { if (it == null) withContext(Dispatchers.IO) { dir.deleteRecursively() } }
            }
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
            throw e
        }
    }

    private suspend fun importChapter(url: String, session: String, dir: File): List<Page>? {
        val app = applicationContext.applicationContext as? Application
            ?: throw IOException("Ce lecteur nécessite Mihon sur Android.")
        val mounted = CompletableDeferred<Activity>()
        var active: Activity? = null
        var destroyed = false
        var button: Button? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == WEBVIEW_ACTIVITY && activity.intent.getStringExtra("url_key") == url) {
                    active = activity
                    mounted.complete(activity)
                }
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === active) destroyed = true
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            val intent = Intent().apply {
                component = ComponentName(app, WEBVIEW_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("url_key", url)
                putExtra("source_key", sourceId)
                putExtra("title_key", "Affichez le chapitre, puis appuyez sur Importer")
            }
            app.startActivity(intent)
            val activity = withTimeout(30.seconds) { mounted.await() }
            val root = activity.findViewById<FrameLayout>(android.R.id.content)
            val webView = withTimeout(30.seconds) {
                var found = findWebView(root)
                while (found == null) {
                    if (destroyed) throw IOException("Import annulé : WebView fermée.")
                    delay(200)
                    found = findWebView(root)
                }
                found
            }
            val start = CompletableDeferred<Unit>()
            button = Button(activity).apply {
                text = "Importer ce chapitre dans Mihon"
                setOnClickListener { start.complete(Unit) }
            }
            root.addView(
                button,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = (24 * activity.resources.displayMetrics.density).roundToInt()
                },
            )
            while (!start.isCompleted) {
                if (destroyed) throw IOException("Import annulé : WebView fermée.")
                delay(200)
            }
            root.removeView(button)
            button = null
            val pages = mutableListOf<Page>()
            var index = 0
            var total: Int? = null
            var offset = 0.0
            var assembler: PageAssembler? = null
            var deadline = TimeSource.Monotonic.markNow()
            try {
                while (coroutineContext.isActive) {
                    if (destroyed || activity.isFinishing) throw IOException("Import annulé : WebView fermée.")
                    if (!activity.hasWindowFocus()) {
                        delay(300)
                        continue
                    }
                    if (deadline.elapsedNow() > 60.seconds) throw IOException("Le site n’a pas rendu la page ${index + 1}. Vérifiez ce chapitre dans la WebView.")
                    // Includes layout and paint after scrolling/changing page; never clones the document.
                    val snapshot = evaluate(webView, "($geometry)(($locator),$index,$offset)")
                    if (snapshot.state == "legacy") return null
                    if (snapshot.state == "error") throw IOException(snapshot.error ?: "Le lecteur du site refuse cette page.")
                    if (snapshot.state != "tile") {
                        delay(500)
                        continue
                    }
                    if (snapshot.index != index || snapshot.total <= index || (total != null && total != snapshot.total)) {
                        throw IOException("L’ordre ou le nombre de pages du chapitre a changé.")
                    }
                    total = snapshot.total
                    delay(450)
                    val stable = evaluate(webView, "($geometry)(($locator),$index,$offset)")
                    if (!snapshot.sameTile(stable)) continue
                    val scale = webView.width / snapshot.viewportWidth
                    val rect = Rect(
                        (snapshot.x * scale).roundToInt(),
                        (snapshot.y * scale).roundToInt(),
                        ((snapshot.x + snapshot.width) * scale).roundToInt(),
                        ((snapshot.y + snapshot.height) * scale).roundToInt(),
                    )
                    if (rect.width() <= 0 || rect.height() <= 0 || rect.left < 0 || rect.top < 0 || rect.right > webView.width || rect.bottom > webView.height) {
                        throw IOException("La page sort de la zone visible. Réinitialisez le zoom de la WebView.")
                    }
                    if (assembler == null) {
                        assembler = PageAssembler(rect.width(), (snapshot.pageHeight * scale).roundToInt()) {
                            save(it, pages, dir, session)
                        }
                    }
                    if (assembler.width != rect.width() || assembler.height != (snapshot.pageHeight * scale).roundToInt()) {
                        throw IOException("L’orientation ou la taille de la page a changé pendant l’import.")
                    }
                    val location = IntArray(2)
                    webView.getLocationInWindow(location)
                    rect.offset(location[0], location[1])
                    val copied = copyWindowPixels(activity.window, rect)
                    val rowsWanted = ((offset + snapshot.height) * scale).roundToInt() - (offset * scale).roundToInt()
                    val tile = if (copied.height == rowsWanted) {
                        copied
                    } else {
                        Bitmap.createScaledBitmap(copied, copied.width, rowsWanted, true).also { copied.recycle() }
                    }
                    try {
                        assembler.append(tile, (offset * scale).roundToInt())
                    } finally {
                        tile.recycle()
                    }
                    offset += snapshot.height
                    deadline = TimeSource.Monotonic.markNow()
                    if (offset >= snapshot.pageHeight - 0.01) {
                        assembler.finish()
                        assembler.close()
                        assembler = null
                        index++
                        offset = 0.0
                        if (index == total) return pages
                    }
                }
                throw IOException("Import interrompu")
            } finally {
                assembler?.close()
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            (button?.parent as? ViewGroup)?.removeView(button)
            active?.takeUnless { it.isFinishing || it.isDestroyed }?.finish()
        }
    }

    private suspend fun save(bitmap: Bitmap, pages: MutableList<Page>, dir: File, session: String) {
        val index = pages.size
        withContext(Dispatchers.IO) {
            File(dir, "$index.png").outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Impossible d’enregistrer la page")
            }
        }
        pages.add(Page(index, imageUrl = "https://$HOST/$sourceId/$session/$index.png"))
    }

    private suspend fun evaluate(webView: WebView, script: String): Tile = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript("JSON.stringify($script)") { value ->
            if (continuation.isActive) {
                try {
                    continuation.resume(value.parseAs<String>().parseAs<Tile>())
                } catch (_: Exception) {
                    continuation.resume(Tile(state = "waiting"))
                }
            }
        }
    }

    @Serializable
    private class Tile(
        val state: String,
        val index: Int = -1,
        val total: Int = 0,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val width: Double = 0.0,
        val height: Double = 0.0,
        val pageHeight: Double = 0.0,
        val viewportWidth: Double = 0.0,
        val error: String? = null,
    ) {
        fun sameTile(other: Tile) = other.state == "tile" && index == other.index && total == other.total &&
            x == other.x && y == other.y && width == other.width && height == other.height && pageHeight == other.pageHeight
    }

    companion object {
        private const val HOST = "reader-window.local"
        private const val WEBVIEW_ACTIVITY = "eu.kanade.tachiyomi.ui.webview.WebViewActivity"
        private val SESSION = Regex("[a-f0-9-]{36}")
        private val FILE = Regex("[0-9]+\\.png")

        private fun findWebView(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
            }
            return null
        }
    }
}

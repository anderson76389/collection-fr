package eu.kanade.tachiyomi.extension.en.comix

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

// Source-local adaptation of keiyoushi/utils/WebView.kt and upstream PR #20047.
// Comix needs native input; keeping its runner here avoids changing every extension's core.
@SuppressLint("SetJavaScriptEnabled")
internal class InputWebViewScope<T>(
    private val view: WebView,
    private val result: CompletableDeferred<T>,
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var destroyed = false

    private var renderDead = false
    private var started: ((String) -> Unit)? = null
    private var finished: ((String) -> Unit)? = null

    @Volatile
    private var intercept: ((WebResourceRequest) -> WebResourceResponse?)? = null

    init {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                guarded { started?.invoke(url.orEmpty()) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                guarded { finished?.invoke(url.orEmpty()) }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (destroyed || request == null) return null
                return try {
                    intercept?.invoke(request)
                } catch (e: Throwable) {
                    reject(e)
                    null
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                renderDead = true
                reject(IOException("Comix WebView render process ended"))
                return true
            }
        }
        val metrics = Resources.getSystem().displayMetrics
        view.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    var userAgent: String
        get() = view.settings.userAgentString
        set(value) {
            view.settings.userAgentString = value
        }

    var blockImages: Boolean
        get() = view.settings.blockNetworkImage
        set(value) {
            view.settings.blockNetworkImage = value
        }

    fun resolve(value: T) {
        result.complete(value)
    }

    fun reject(error: Throwable) {
        result.completeExceptionally(error)
    }

    fun onPageStarted(block: (String) -> Unit) {
        started = block
    }

    fun onPageFinished(block: (String) -> Unit) {
        finished = block
    }

    fun interceptRequest(block: (WebResourceRequest) -> WebResourceResponse?) {
        intercept = block
    }

    fun jsBridge(name: String, block: (String) -> Unit) {
        view.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun post(message: String) {
                    guarded { block(message) }
                }
            },
            name,
        )
    }

    fun loadData(baseUrl: String, html: String) {
        view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    fun evaluateJs(script: String, callback: ((String) -> Unit)? = null) {
        onMain {
            view.evaluateJavascript(script) { value -> guarded { callback?.invoke(value) } }
        }
    }

    fun poll(interval: Duration, block: () -> Unit) {
        val task = object : Runnable {
            override fun run() {
                if (destroyed || result.isCompleted) return
                guarded(block)
                if (!destroyed && !result.isCompleted) handler.postDelayed(this, interval.inWholeMilliseconds)
            }
        }
        handler.postDelayed(task, interval.inWholeMilliseconds)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        evaluateJs("window.devicePixelRatio") { ratio ->
            val scale = ratio.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
                ?: Resources.getSystem().displayMetrics.density
            val frames = Random.nextInt(25, 37)
            val downTime = SystemClock.uptimeMillis()
            fun send(frame: Int) {
                if (destroyed || result.isCompleted) return
                guarded {
                    val action = when (frame) {
                        0 -> MotionEvent.ACTION_DOWN
                        frames + 1 -> MotionEvent.ACTION_UP
                        else -> MotionEvent.ACTION_MOVE
                    }
                    val progress = 1 - (1 - (frame.coerceAtMost(frames).toFloat() / frames).pow(1.6f)).pow(4)
                    val major = Random.nextInt(7, 12).toFloat()
                    val properties = MotionEvent.PointerProperties().apply { toolType = MotionEvent.TOOL_TYPE_FINGER }
                    val coords = MotionEvent.PointerCoords().apply {
                        x = (fromX + (toX - fromX) * progress) * scale
                        y = (fromY + (toY - fromY) * progress) * scale
                        pressure = 1f
                        size = major / 255
                        touchMajor = major
                        touchMinor = Random.nextInt(6, major.toInt() + 1).toFloat()
                        toolMajor = touchMajor
                        toolMinor = touchMinor
                    }
                    val event = MotionEvent.obtain(
                        downTime, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coords),
                        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
                    )
                    try {
                        view.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                    if (frame <= frames) handler.postDelayed({ send(frame + 1) }, Random.nextLong(14, 19))
                }
            }
            send(0)
        }
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        if (!renderDead) view.stopLoading()
        view.destroy()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) guarded(block) else handler.post { guarded(block) }
    }

    private fun guarded(block: () -> Unit) {
        if (destroyed || result.isCompleted) return
        try {
            block()
        } catch (e: Throwable) {
            reject(e)
        }
    }
}

internal suspend fun <T> runInputWebView(
    timeout: Duration,
    configure: InputWebViewScope<T>.() -> Unit,
): T = withContext(Dispatchers.Main) {
    val result = CompletableDeferred<T>()
    val view = WebView(applicationContext)
    var scope: InputWebViewScope<T>? = null
    try {
        scope = InputWebViewScope(view, result)
        scope.configure()
        try {
            withTimeout(timeout) { result.await() }
        } catch (_: TimeoutCancellationException) {
            throw IOException("Timed out waiting for Comix WebView")
        }
    } finally {
        if (scope != null) scope.destroy() else view.destroy()
    }
}

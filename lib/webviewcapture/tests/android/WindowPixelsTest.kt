package keiyoushi.lib.webviewcapture

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.view.WindowManager
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class WindowPixelsTest {
    @Test
    fun copiesWorkerRenderedSurfaceWhenCanvasExportIsBlocked() {
        fixture { activity ->
            assertEquals(
                "\"SecurityError\"",
                js(
                    activity.webView,
                    """
                (() => { try { document.querySelector('canvas').toDataURL(); return 'unexpected'; }
                catch (e) { return e.name; } })()
                    """.trimIndent(),
                ),
            )
            val position = IntArray(2)
            activity.webView.getLocationInWindow(position)
            val rect = Rect(position[0], position[1], position[0] + activity.webView.width, position[1] + 200)
            val bitmap = copyWindowPixels(activity.window, rect)
            try {
                assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, 80))
                assertEquals(rect.width(), bitmap.width)
                assertEquals(rect.height(), bitmap.height)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun respectsSecureWindows() {
        fixture { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            var rejected = false
            try {
                copyWindowPixels(activity.window, Rect(0, 0, 100, 100)).recycle()
            } catch (_: IOException) {
                rejected = true
            }
            assertTrue("Secure windows must never be copied", rejected)
        }
    }

    private fun fixture(block: suspend (SurfaceFixtureActivity) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent(context, SurfaceFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SurfaceFixtureActivity>(intent).use { scenario ->
            lateinit var activity: SurfaceFixtureActivity
            scenario.onActivity { activity = it }
            runBlocking {
                withContext(Dispatchers.Main) {
                    withTimeout(30_000) {
                        while (js(activity.webView, "document.body?.dataset.ready") != "\"true\"") delay(100)
                    }
                    delay(500)
                    block(activity)
                }
            }
        }
    }

    private suspend fun js(webView: WebView, script: String): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it) }
    }
}

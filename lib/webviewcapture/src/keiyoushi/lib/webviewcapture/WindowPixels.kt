package keiyoushi.lib.webviewcapture

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// The rendered surface is copied by Android, without reading the site's canvas APIs.
internal suspend fun copyWindowPixels(window: Window, rect: Rect): Bitmap = suspendCancellableCoroutine { continuation ->
    if (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
        continuation.resumeWithException(IOException("Le site ou l’application interdit la capture de cette fenêtre."))
        return@suspendCancellableCoroutine
    }
    val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(window, rect, bitmap, { status ->
            if (!continuation.isActive) {
                bitmap.recycle()
            } else if (status == PixelCopy.SUCCESS) {
                continuation.resume(bitmap) { _, value, _ -> value.recycle() }
            } else {
                bitmap.recycle()
                continuation.resumeWithException(IOException("Android n’a pas pu transférer la page (PixelCopy $status)."))
            }
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        bitmap.recycle()
        if (continuation.isActive) continuation.resumeWithException(e)
    }
}

package keiyoushi.lib.webviewcapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import java.io.Closeable
import java.io.IOException

// Only a complete, contiguous page is emitted. Very tall pages are split without missing rows.
internal class PageAssembler(
    val width: Int,
    val height: Int,
    private val maxPixels: Int = 8_000_000,
    private val emit: suspend (Bitmap) -> Unit,
) : Closeable {
    private var bitmap: Bitmap? = null
    private var rows = 0
    private var partTop = 0

    suspend fun append(tile: Bitmap, top: Int) {
        if (tile.width != width || top != rows || top + tile.height > height) {
            throw IOException("Les dimensions ou l’ordre des pages ont changé pendant l’import.")
        }
        var consumed = 0
        while (consumed < tile.height) {
            val target = bitmap ?: Bitmap.createBitmap(width, minOf(height - partTop, maxPixels / width), Bitmap.Config.ARGB_8888).also { bitmap = it }
            val destinationTop = rows - partTop
            val count = minOf(tile.height - consumed, target.height - destinationTop)
            Canvas(target).drawBitmap(tile, Rect(0, consumed, width, consumed + count), Rect(0, destinationTop, width, destinationTop + count), null)
            rows += count
            consumed += count
            if (rows - partTop == target.height) {
                emit(target)
                partTop = rows
                target.recycle()
                bitmap = null
            }
        }
    }

    fun finish() {
        if (rows != height || bitmap != null) throw IOException("La page n’a pas été transférée en entier ($rows/$height lignes).")
    }

    override fun close() {
        bitmap?.recycle()
        bitmap = null
    }
}

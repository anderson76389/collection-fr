package eu.kanade.tachiyomi.extension.fr.animesama

import kotlin.math.min
import kotlin.math.roundToInt

internal const val COVER_MARKER = "makoff-cover-v2"

internal fun String.coverUrl(): String = substringBefore('#') + "#" + COVER_MARKER

internal class CoverLayout(val width: Int, val height: Int, val left: Float, val top: Float, val imageWidth: Float, val imageHeight: Float)

// Mihon's portrait cards crop horizontal artwork; fit the entire original inside a 2:3 canvas.
internal fun coverLayout(width: Int, height: Int): CoverLayout {
    require(width > 0 && height > 0)
    val canvasWidth = maxOf(width.toFloat(), height * 2f / 3f)
    val scale = min(1f, 1000f / canvasWidth)
    val outputWidth = (canvasWidth * scale).roundToInt().coerceAtLeast(2)
    val outputHeight = (outputWidth * 1.5f).roundToInt()
    val imageScale = min(outputWidth.toFloat() / width, outputHeight.toFloat() / height)
    val imageWidth = width * imageScale
    val imageHeight = height * imageScale
    return CoverLayout(outputWidth, outputHeight, (outputWidth - imageWidth) / 2f, (outputHeight - imageHeight) / 2f, imageWidth, imageHeight)
}

internal fun coverSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= 1000 || height / (sample * 2) >= 1500) sample *= 2
    return sample
}

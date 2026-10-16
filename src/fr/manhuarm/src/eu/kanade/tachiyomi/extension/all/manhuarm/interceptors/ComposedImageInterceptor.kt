package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.extension.all.manhuarm.Dialog
import eu.kanade.tachiyomi.extension.all.manhuarm.Language
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

// Draws the OCR dialogues onto the page image.
class ComposedImageInterceptor(
    private val settings: () -> Language,
) : Interceptor {

    private val fonts = ConcurrentHashMap<String, Typeface>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val dialogues = request.url.fragment?.takeIf { it.startsWith("[") }?.parseAs<List<Dialog>>()
            ?: return chain.proceed(request)
        val response = chain.proceed(request)

        if (!response.isSuccessful || dialogues.isEmpty()) {
            return response
        }

        val language = settings()
        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = response.body.byteStream().use { BitmapFactory.decodeStream(it, null, options) }!!
        val canvas = Canvas(bitmap)
        val font = selectFontFamily(language)
        val textPaint = createTextPaint(language, font)
        val initialTextSize = textPaint.textSize

        dialogues.forEach { dialog ->
            dialog.scale = language.dialogBoxScale
            if (dialog.width < 1f || dialog.height < 1f) return@forEach
            textPaint.textSize = initialTextSize
            val dialogBox = createDialogBox(language, dialog, textPaint)
            val y = getYAxis(textPaint, dialog, dialogBox)
            canvas.draw(textPaint, dialogBox, dialog, dialog.x, y)
        }

        val (format, mediaType) = when (request.url.pathSegments.last().substringAfterLast(".").lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG to "image/png"
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG to "image/jpeg"
            else -> Bitmap.CompressFormat.WEBP to "image/webp"
        }

        val output = Buffer()
        bitmap.compress(format, 100, output.outputStream())
        bitmap.recycle()

        return response.newBuilder()
            .body(output.asResponseBody(mediaType.toMediaType(), output.size))
            .build()
    }

    private fun createTextPaint(language: Language, font: Typeface?): TextPaint = TextPaint().apply {
        color = Color.BLACK
        textSize = language.fontSize / SCALED_DENSITY
        font?.let { typeface = it }
        isAntiAlias = true
    }

    private fun selectFontFamily(language: Language): Typeface? {
        if (language.disableFontSettings) {
            return null
        }
        return loadFont("${language.fontName}.ttf")
    }

    /**
     * Loads a font from the `assets/fonts` directory within the APK.
     */
    private fun loadFont(fontName: String): Typeface? = fonts[fontName] ?: try {
        val fontFile = File.createTempFile(fontName, ".ttf")
        this::class.java.classLoader!!.getResourceAsStream("assets/fonts/$fontName")!!.use { input ->
            fontFile.outputStream().use(input::copyTo)
        }
        Typeface.createFromFile(fontFile).also {
            fontFile.delete()
            fonts[fontName] = it
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Adjust the text to the center of the dialog box when feasible.
     */
    private fun getYAxis(textPaint: TextPaint, dialog: Dialog, dialogBox: StaticLayout): Float {
        val fontHeight = textPaint.fontMetrics.let { it.bottom - it.top }

        val dialogBoxLineCount = dialog.height / fontHeight

        // Centers text in y for dialogues smaller than the dialog box
        return when {
            dialogBox.lineCount < dialogBoxLineCount -> dialog.centerY - dialogBox.lineCount / 2f * fontHeight
            else -> dialog.y
        }
    }

    private fun createDialogBox(language: Language, dialog: Dialog, textPaint: TextPaint): StaticLayout {
        val text = Jsoup.parse(dialog.getTextBy(language)).text()
        val initialTextSize = textPaint.textSize
        var dialogBox = createBoxLayout(language, dialog, textPaint, text)

        if (dialogBox.height > dialog.height) {
            // Find the largest fitting half-pixel step without rebuilding every intermediate layout.
            var low = 1
            var high = ceil((initialTextSize - 1f) / 0.5f).toInt().coerceAtLeast(0)
            var bestSize = 1f
            textPaint.textSize = bestSize
            dialogBox = createBoxLayout(language, dialog, textPaint, text)
            while (low <= high) {
                val step = low + (high - low) / 2
                textPaint.textSize = (initialTextSize - step * 0.5f).coerceAtLeast(1f)
                val candidate = createBoxLayout(language, dialog, textPaint, text)
                if (candidate.height <= dialog.height) {
                    bestSize = textPaint.textSize
                    dialogBox = candidate
                    high = step - 1
                } else {
                    low = step + 1
                }
            }
            textPaint.textSize = bestSize
        }

        textPaint.color = Color.BLACK
        textPaint.bgColor = Color.WHITE

        return dialogBox
    }

    private fun createBoxLayout(language: Language, dialog: Dialog, textPaint: TextPaint, text: String): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, dialog.width.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(language.disableFontSettings)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (language.disableWordBreak) {
                    setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    return@apply
                }
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

    private fun Canvas.draw(textPaint: TextPaint, layout: StaticLayout, dialog: Dialog, x: Float, y: Float) {
        save()
        translate(x, y)
        rotate(dialog.angle)
        drawTextOutline(textPaint, layout)
        drawText(textPaint, layout)
        restore()
    }

    private fun Canvas.drawText(textPaint: TextPaint, layout: StaticLayout) {
        textPaint.style = Paint.Style.FILL
        layout.draw(this)
    }

    private fun Canvas.drawTextOutline(textPaint: TextPaint, layout: StaticLayout) {
        val foregroundColor = textPaint.color
        val style = textPaint.style

        textPaint.strokeWidth = 5F
        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE

        layout.draw(this)

        textPaint.color = foregroundColor
        textPaint.style = style
    }

    companion object {
        // w3: Absolute Lengths [...](https://www.w3.org/TR/css3-values/#absolute-lengths)
        private const val SCALED_DENSITY = 0.75f // 1px = 0.75pt
    }
}

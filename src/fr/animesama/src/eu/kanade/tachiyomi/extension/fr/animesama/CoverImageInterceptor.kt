package eu.kanade.tachiyomi.extension.fr.animesama

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

internal class CoverImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful || chain.request().url.fragment != COVER_MARKER) return response
        val bytes = response.body.bytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = coverSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return response.newBuilder().body(bytes.toResponseBody(response.body.contentType())).build()
        try {
            val layout = coverLayout(original.width, original.height)
            val cover = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(cover)
                canvas.drawColor(Color.rgb(24, 24, 28))
                val destination = RectF(layout.left, layout.top, layout.left + layout.imageWidth, layout.top + layout.imageHeight)
                canvas.drawBitmap(original, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                val output = ByteArrayOutputStream()
                cover.compress(Bitmap.CompressFormat.JPEG, 95, output)
                return response.newBuilder()
                    .removeHeader("Content-Length")
                    .removeHeader("Content-Encoding")
                    .header("Content-Type", "image/jpeg")
                    .body(output.toByteArray().toResponseBody("image/jpeg".toMediaType()))
                    .build()
            } finally {
                cover.recycle()
            }
        } finally {
            original.recycle()
        }
    }
}

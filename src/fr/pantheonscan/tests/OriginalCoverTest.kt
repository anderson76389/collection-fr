package eu.kanade.tachiyomi.extension.fr.pantheonscan

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalCoverTest {
    private val thumbnail = "https://covers.example/wp-content/uploads/2026/07/cover_250x350-1-193x278.jpg?token=a%2Bb"

    @Test
    fun generatedThumbnailUsesItsOriginalWithoutChangingFilenameOrQuery() {
        val url = originalCoverUrl(thumbnail)!!.toHttpUrl()
        assertEquals("/wp-content/uploads/2026/07/cover_250x350-1.jpg", url.encodedPath)
        assertEquals("a+b", url.queryParameter("token"))
    }

    @Test
    fun actualOriginalAndUnrelatedUrlsArePreserved() {
        val original = "https://covers.example/wp-content/uploads/2026/07/cover_250x350-1.jpg"
        assertEquals(original, originalCoverUrl(original))
        val chapter = "https://covers.example/reader/page-193x278.jpg"
        assertEquals(chapter, originalCoverUrl(chapter))
        assertEquals(null, originalCoverUrl(null))
    }

    @Test
    fun missingOriginalFallsBackToTheExactThumbnailWithTheSameHeaders() {
        for (status in listOf(404, 410)) {
            val paths = mutableListOf<String>()
            val client = OkHttpClient.Builder().addInterceptor(OriginalCoverInterceptor()).addInterceptor { chain ->
                paths.add(chain.request().url.toString())
                assertEquals("https://covers.example/", chain.request().header("Referer"))
                reply(chain, if (paths.size == 1) status else 200)
            }.build()
            val request = Request.Builder().url(originalCoverUrl(thumbnail)!!).header("Referer", "https://covers.example/").build()
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals(2, paths.size)
            assertEquals(thumbnail, paths.last())
        }
    }

    @Test
    fun successfulOriginalBytesAreReturnedWithoutResizingOrReencoding() {
        var calls = 0
        val bytes = byteArrayOf(-1, -40, 0, 1, 2, 3, -1, -39)
        val client = OkHttpClient.Builder().addInterceptor(OriginalCoverInterceptor()).addInterceptor { chain ->
            calls++
            reply(chain, 200).newBuilder().body(bytes.toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        client.newCall(Request.Builder().url(originalCoverUrl(thumbnail)!!).build()).execute().use {
            assertArrayEquals(bytes, it.body.bytes())
        }
        assertEquals(1, calls)
    }

    @Test
    fun chapterErrorsAreNotRetriedAsCovers() {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(OriginalCoverInterceptor()).addInterceptor { chain ->
            calls++
            reply(chain, 404)
        }.build()
        client.newCall(Request.Builder().url("https://covers.example/reader/chapter-1").build()).execute().use {
            assertEquals(404, it.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun serverFailuresDoNotCauseExtraRequests() {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(OriginalCoverInterceptor()).addInterceptor { chain ->
            calls++
            reply(chain, 503)
        }.build()
        client.newCall(Request.Builder().url(originalCoverUrl(thumbnail)!!).build()).execute().use {
            assertEquals(503, it.code)
        }
        assertEquals(1, calls)
    }

    private fun reply(chain: Interceptor.Chain, code: Int) = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body("image".toResponseBody()).build()
}

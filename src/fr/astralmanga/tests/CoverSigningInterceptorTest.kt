package eu.kanade.tachiyomi.extension.fr.astralmanga

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CoverSigningInterceptorTest {
    @Test
    fun catalogueCoverIdentifiersDoNotTriggerAnySigningRequests() {
        var calls = 0
        val interceptor = CoverSigningInterceptor({ "https://astral-manga.fr" }) {
            calls++
            "https://images.example/cover.png"
        }
        val urls = (1..12).map { interceptor.coverUrl("uploads/cover $it.png") }
        assertEquals(0, calls)
        assertEquals(12, urls.distinct().size)
        assertEquals(urls, (1..12).map { interceptor.coverUrl("uploads/cover $it.png") })
    }

    @Test
    fun actualImageFetchSignsOnceAndPreservesTheKeyAndHeaders() {
        var calls = 0
        var requestedKey = ""
        var destination = ""
        val interceptor = CoverSigningInterceptor({ "https://astral-manga.fr" }) { key ->
            calls++
            requestedKey = key
            "https://images.example/cover.png?token=temporary"
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
            destination = chain.request().url.toString()
            assertEquals("https://astral-manga.fr/", chain.request().header("Referer"))
            response(chain)
        }.build()
        val key = "uploads/a b/épreuve+.png"
        val request = Request.Builder().url(interceptor.coverUrl(key)).header("Referer", "https://astral-manga.fr/").build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
        assertEquals(1, calls)
        assertEquals(key, requestedKey)
        assertEquals("https://images.example/cover.png?token=temporary", destination)
    }

    @Test
    fun ordinaryRequestsAreNotSignedOrRewritten() {
        var calls = 0
        val interceptor = CoverSigningInterceptor({ "https://astral-manga.fr" }) {
            calls++
            null
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
            assertEquals("https://astral-manga.fr/api/mangas?page=1", chain.request().url.toString())
            response(chain)
        }.build()
        client.newCall(Request.Builder().url("https://astral-manga.fr/api/mangas?page=1").build()).execute().close()
        assertEquals(0, calls)
    }

    @Test
    fun aSigningFailureIsReportedInsteadOfRequestingTheVirtualUrl() {
        val interceptor = CoverSigningInterceptor({ "https://astral-manga.fr" }) { null }
        var sent = false
        val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
            sent = true
            response(chain)
        }.build()
        var rejected = false
        try {
            client.newCall(Request.Builder().url(interceptor.coverUrl("missing")).build()).execute().close()
        } catch (_: IOException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(false, sent)
    }

    private fun response(chain: Interceptor.Chain): Response = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("image".toResponseBody()).build()
}

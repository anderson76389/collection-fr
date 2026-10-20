package eu.kanade.tachiyomi.extension.fr.raijinscans

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AniverseTransportTest {
    private fun response(request: Request, status: Int, closed: AtomicBoolean) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("Test")
        .body(object : ResponseBody() {
            private val buffer = Buffer().writeUtf8("network")
            override fun contentType() = null
            override fun contentLength() = 7L
            override fun source(): BufferedSource = buffer
            override fun close() { closed.set(true); super.close() }
        })
        .build()

    @Test
    fun successfulResponsesAndImagesDoNotOpenWebView() {
        for ((url, status) in listOf(
            "https://aniverse.fr/api/manga?page=1" to 200,
            "https://cdn.onefy.me/page.webp" to 403,
            "https://aniverse.fr/api/manga?page=1" to 500,
        )) {
            val closed = AtomicBoolean()
            var browserCalls = 0
            val client = OkHttpClient.Builder()
                .addInterceptor(AniverseTransport("aniverse.fr") { _, request ->
                    browserCalls++
                    browserResponse(request, "browser", true)
                })
                .addInterceptor { response(it.request(), status, closed) }
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use {
                assertEquals(status, it.code)
                assertEquals("network", it.body.string())
            }
            assertEquals(0, browserCalls)
        }
    }

    @Test
    fun refusedSiteRequestsCloseTheirResponseAndRetryOnceThroughBrowser() {
        for (status in listOf(403, 503)) {
            val closed = AtomicBoolean()
            var browserCalls = 0
            val client = OkHttpClient.Builder()
                .addInterceptor(AniverseTransport("aniverse.fr") { _, request ->
                    assertTrue(closed.get())
                    browserCalls++
                    browserResponse(request, """{"items":[]}""", true)
                })
                .addInterceptor { response(it.request(), status, closed) }
                .build()
            client.newCall(Request.Builder().url("https://aniverse.fr/api/manga?page=2").build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("application/json; charset=utf-8", it.body.contentType().toString())
                assertEquals("""{"items":[]}""", it.body.string())
                assertEquals("page=2", it.request.url.query)
            }
            assertEquals(1, browserCalls)
        }
    }

    @Test
    fun browserFailureIsReportedWithoutRetryLoop() {
        val closed = AtomicBoolean()
        var browserCalls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(AniverseTransport("aniverse.fr") { _, _ ->
                browserCalls++
                throw IOException("Verification required")
            })
            .addInterceptor { response(it.request(), 403, closed) }
            .build()
        var failed = false
        try {
            client.newCall(Request.Builder().url("https://aniverse.fr/manga/one-piece").build()).execute().close()
        } catch (error: IOException) {
            failed = true
            assertEquals("Verification required", error.message)
        }
        assertTrue(failed)
        assertTrue(closed.get())
        assertEquals(1, browserCalls)
    }

    @Test
    fun htmlPayloadUsesHtmlContentTypeAndKeepsItsUrl() {
        val request = Request.Builder().url("https://aniverse.fr/manga/one-piece").build()
        browserResponse(request, "<html>reader</html>", false).use {
            assertEquals("text/html; charset=utf-8", it.body.contentType().toString())
            assertEquals(request.url, it.request.url)
            assertFalse(it.body.string().isBlank())
        }
    }
}

package eu.kanade.tachiyomi.extension.fr.raijinscans

import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

internal class AniverseTransport(
    private val siteHost: String,
    private val browser: (Call, Request) -> Response,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET" || request.url.host != siteHost || response.code !in setOf(403, 503)) return response
        response.close()
        return browser(chain.call(), request)
    }
}

@Serializable
internal class BrowserResult(val status: Int, val body: String)

internal fun browserResponse(request: Request, body: String, api: Boolean): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("OK")
    .body(body.toResponseBody((if (api) "application/json" else "text/html").toMediaType()))
    .build()

package eu.kanade.tachiyomi.extension.fr.astralmanga

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// Stable cover identifiers let Mihon's image cache work without waiting for signing at catalogue load.
internal class CoverSigningInterceptor(
    private val baseUrl: () -> String,
    private val sign: (String) -> String?,
) : Interceptor {
    fun coverUrl(key: String): String = baseUrl().toHttpUrl().newBuilder()
        .addPathSegment(COVER_PATH)
        .addQueryParameter("key", key)
        .build().toString()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrl().toHttpUrl().host || request.url.encodedPath != "/$COVER_PATH") {
            return chain.proceed(request)
        }
        val key = request.url.queryParameter("key")?.takeIf(String::isNotBlank)
            ?: throw IOException("Astral-Manga : clé de couverture absente")
        val imageUrl = sign(key)?.toHttpUrl()
            ?: throw IOException("Astral-Manga : le site n’a pas fourni la couverture")
        return chain.proceed(request.newBuilder().url(imageUrl).build())
    }

    companion object {
        private const val COVER_PATH = "makoff-cover"
    }
}

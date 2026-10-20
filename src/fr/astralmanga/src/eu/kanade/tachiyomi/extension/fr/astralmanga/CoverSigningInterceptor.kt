package eu.kanade.tachiyomi.extension.fr.astralmanga

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// Resolve both covers and chapter pages on image fetch, never serially while building a list.
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
            ?: throw IOException("Astral-Manga : clé d’image absente")
        val imageUrl = sign(key)?.toHttpUrl()
            ?: throw IOException("Astral-Manga : le site n’a pas fourni l’image")
        return chain.proceed(request.newBuilder().url(imageUrl).build())
    }

    companion object {
        private const val COVER_PATH = "makoff-cover"
    }
}

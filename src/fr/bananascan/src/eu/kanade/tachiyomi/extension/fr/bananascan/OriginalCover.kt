package eu.kanade.tachiyomi.extension.fr.bananascan

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

private const val FALLBACK_MARKER = "makoff-cover-original:"
private val RESIZED_IMAGE = Regex("-\\d+x\\d+(?=\\.(?:jpe?g|png|webp|gif)$)", RegexOption.IGNORE_CASE)

internal fun originalCoverUrl(value: String?): String? {
    val url = value?.toHttpUrlOrNull() ?: return value
    if (!url.encodedPath.startsWith("/wp-content/uploads/")) return value
    val path = url.encodedPath.replace(RESIZED_IMAGE, "")
    if (path == url.encodedPath) return value
    return url.newBuilder().encodedPath(path).fragment(FALLBACK_MARKER + url.toString()).build().toString()
}

// Some older uploads retain only the generated thumbnail. Keep those covers working.
internal class OriginalCoverInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 404 && response.code != 410) return response
        val fallback = request.url.fragment?.takeIf { it.startsWith(FALLBACK_MARKER) }
            ?.removePrefix(FALLBACK_MARKER)?.toHttpUrlOrNull()
            ?.takeIf { it.host == request.url.host && it.encodedPath.startsWith("/wp-content/uploads/") }
            ?: return response
        response.close()
        return chain.proceed(request.newBuilder().url(fallback).build())
    }
}

package eu.kanade.tachiyomi.extension.fr.bananascan

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Fail with an actionable error when Harmony's origin is offline.
 * This cannot restore chapters while the original site is unavailable.
 */
internal class HarmonyAvailabilityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in HARMONY_HOSTS) return chain.proceed(request)

        val response = try {
            chain.proceed(request)
        } catch (error: UnknownHostException) {
            throw IOException(HARMONY_OFFLINE_MESSAGE, error)
        } catch (error: SocketTimeoutException) {
            throw IOException(HARMONY_OFFLINE_MESSAGE, error)
        }

        if (harmonyServerIsUnavailable(response.code)) {
            val status = response.code
            response.close()
            throw IOException("$HARMONY_OFFLINE_MESSAGE (HTTP $status)")
        }
        return response
    }
}

internal fun harmonyServerIsUnavailable(code: Int): Boolean = code == 502 || code == 503 || code == 504

private val HARMONY_HOSTS = setOf("harmony-scan.fr", "www.harmony-scan.fr")
private const val HARMONY_OFFLINE_MESSAGE =
    "Harmony-Scan : le site officiel ne répond plus. Aucune extension ne peut récupérer ses chapitres tant que le serveur n'est pas rétabli."

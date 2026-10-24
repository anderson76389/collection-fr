package eu.kanade.tachiyomi.multisrc.pam

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class SitePage(
    val page: JsonObject,
    val fetched: JsonElement? = null,
    val chapters: List<ChapterListItem>? = null,
    val reader: ReaderResult? = null,
    val pageHeaders: Map<String, String> = emptyMap(),
) {
    fun fetched() = fetched ?: throw IOException("Missing API response")
}

/** Loads the site's pages in a WebView, one at a time, as its own readers do, and reads the site's data from them. */
internal class SitePages(
    baseUrl: String,
    private val client: () -> OkHttpClient,
    private val userAgent: () -> String,
    private val allowMissingReaderPageRequest: Boolean = false,
) {
    private val baseHttpUrl = baseUrl.toHttpUrl()

    private val mutex = Mutex()

    private var lastLoad: TimeMark? = null

    private class Failure(val at: TimeMark, val challengeClearance: String?)

    /** Set when the site blocked or challenged us; loads fail fast for [BACKOFF], or until a challenge is passed in WebView. */
    @Volatile
    private var failure: Failure? = null

    private fun clearance() = client().cookieJar.loadForRequest(baseHttpUrl).firstOrNull { it.name == "cf_clearance" }?.value

    private fun failed(challenge: Boolean) {
        failure = Failure(TimeSource.Monotonic.markNow(), if (challenge) clearance().orEmpty() else null)
    }

    private fun Failure.isActive(): Boolean {
        if (at.elapsedNow() >= BACKOFF) return false
        return challengeClearance == null || clearance().let { it == null || it == challengeClearance }
    }

    /** Loads [url] and reads its Inertia page data; with [reader], also the reader v2 manifest, page key and page request headers. */
    suspend fun load(url: String, request: SiteRequest = SiteRequest(), reader: Boolean = false): SitePage = mutex.withLock {
        if (failure?.isActive() == true) throw IOException(SOLVE_IN_WEBVIEW)
        lastLoad?.let { delay(LOAD_INTERVAL - it.elapsedNow()) }
        lastLoad = TimeSource.Monotonic.markNow()

        // Public catalogue pages already contain their Inertia data; the reader still needs WebView.
        if (!reader && request.isPlainPage()) {
            val httpRequest = Request.Builder().url(url).header("User-Agent", userAgent()).header("Accept", "text/html").build()
            client().newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    serverPageData(response.asJsoup())?.let {
                        return@withLock SitePage(it.parseAs<JsonObject>())
                    }
                }
            }
        }

        val bridge = "k" + ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val readScript = "(${asset("site.js")})(\"$bridge\", ${request.toJsonString()})"
        val readerScript = if (reader) "(${asset("reader.js")})(\"$bridge\")" else null
        var site: SitePage? = null
        val hasPage = AtomicBoolean(false)
        val hasReaderV2 = AtomicBoolean(false)
        val hasReaderResult = AtomicBoolean(false)
        val hasPageRequest = AtomicBoolean(false)
        var challengePolls = 0
        val result = try {
            runWebView<SitePage>(timeout = WEBVIEW_TIMEOUT) {
                userAgent = this@SitePages.userAgent()
                // Catalogue screenshots are not needed; the reader v2 needs its /p/ image
                // requests to fire so the page headers and manifest can be collected.
                blockImages = !reader
                val lock = Any()
                var readerV2 = false
                var readerResult: ReaderResult? = null
                val pageRequests = mutableListOf<Pair<String, Map<String, String>>>()
                var readerMetadataReadyAt: TimeMark? = null
                var readerNavigationAt = TimeSource.Monotonic.markNow()
                var readerRetried = false
                fun resolveWhenReady() {
                    val page = site ?: return
                    if (!readerV2) return resolve(page)
                    val captured = readerResult ?: return
                    val pageHeaders = pageRequests.firstOrNull { (path, _) -> path.startsWith(captured.manifest.base) }?.second
                    if (pageHeaders != null) {
                        resolve(SitePage(page.page, reader = captured, pageHeaders = pageHeaders))
                    } else if (allowMissingReaderPageRequest) {
                        // On Soft Epsilon the off-screen reader may provide its manifest
                        // and key without issuing an image request. Do not wait 60 seconds
                        // for headers that may never arrive: the native image request can
                        // use its chapter Referer and surface an actual HTTP error.
                        val since = readerMetadataReadyAt
                        if (since == null) {
                            readerMetadataReadyAt = TimeSource.Monotonic.markNow()
                        } else if (pamReaderFallbackAllowed(allowMissingReaderPageRequest, since.elapsedNow())) {
                            resolve(SitePage(page.page, reader = captured))
                        }
                    }
                }
                // Keep reader image requests unblocked to avoid deadlocking the page scripts.
                // Catalogue filtering remains unchanged.
                interceptRequest { webRequest ->
                    val requestUrl = webRequest.url
                    val requestHost = requestUrl.host
                    if (!reader && requestHost != null && requestHost != baseHttpUrl.host && requestHost != TURNSTILE_HOST) return@interceptRequest blockedResponse()
                    val path = requestUrl.path.orEmpty()
                    if (UNUSED_PATHS.any(path::startsWith)) return@interceptRequest blockedResponse()
                    // Styles and fonts only for the reader's fingerprint
                    if (!reader && STYLE_EXTENSIONS.any(path::endsWith)) return@interceptRequest emptyResponse()
                    if (reader && requestUrl.getQueryParameter(bridge) != null) {
                        return@interceptRequest rewrittenWorker(webRequest, bridge)
                    }
                    if (!reader || !path.startsWith("/p/")) return@interceptRequest null
                    synchronized(lock) {
                        pageRequests += path to webRequest.requestHeaders
                        hasPageRequest.set(true)
                        resolveWhenReady()
                    }
                    null
                }
                jsBridge(bridge) { posted ->
                    val message = posted.parseAs<SiteMessage>()
                    synchronized(lock) {
                        when {
                            message.reader != null -> {
                                readerResult = message.reader
                                hasReaderResult.set(true)
                            }
                            message.error != null -> {
                                if (message.status == 403 || message.status == 429) failed(challenge = message.status == 403)
                                return@jsBridge reject(IOException(message.error))
                            }
                            else -> {
                                val page = message.page ?: return@jsBridge reject(IOException("Missing page data"))
                                readerV2 = reader && page.parseAs<PageListResponse>().props.readerV2
                                hasReaderV2.set(readerV2)
                                site = SitePage(page, message.fetched, message.chapters)
                                hasPage.set(true)
                            }
                        }
                        resolveWhenReady()
                    }
                }
                if (readerScript != null) {
                    injectAtDocumentStart(readerScript, setOf("${baseHttpUrl.scheme}://${baseHttpUrl.host}"))
                }
                onReceivedError { webRequest, error ->
                    if (webRequest.isForMainFrame) reject(IOException(error.description.toString()))
                }
                // Challenges that pass by themselves take a few seconds; the rest need the user
                poll(POLL_INTERVAL) {
                    if (reader && allowMissingReaderPageRequest) {
                        synchronized(lock) {
                            resolveWhenReady()
                            // A chapter can serve Inertia metadata but start its encrypted
                            // worker too early for document-start instrumentation to attach.
                            // Retry the navigation only once, and only for Epsilon Scan.
                            if (pamReaderNavigationRetryAllowed(
                                    enabled = allowMissingReaderPageRequest,
                                    hasPage = hasPage.get(),
                                    isReaderV2 = hasReaderV2.get(),
                                    hasReaderResult = hasReaderResult.get(),
                                    alreadyRetried = readerRetried,
                                    wait = readerNavigationAt.elapsedNow(),
                                )
                            ) {
                                readerRetried = true
                                site = null
                                readerV2 = false
                                readerResult = null
                                readerMetadataReadyAt = null
                                pageRequests.clear()
                                hasPage.set(false)
                                hasReaderV2.set(false)
                                hasReaderResult.set(false)
                                hasPageRequest.set(false)
                                readerNavigationAt = TimeSource.Monotonic.markNow()
                                loadUrl(url)
                            }
                        }
                    }
                    evaluateJs(readScript) {
                        if (it != "\"challenge\"") {
                            challengePolls = 0
                        } else if (POLL_INTERVAL * ++challengePolls > CHALLENGE_WAIT) {
                            failed(challenge = true)
                            reject(IOException(SOLVE_IN_WEBVIEW))
                        }
                    }
                }
                loadUrl(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: WebViewTimeoutException) {
            if (challengePolls > 0) {
                failed(challenge = true)
                throw IOException(SOLVE_IN_WEBVIEW, e)
            }
            val message = pamReaderTimeoutReason(
                hasPage = hasPage.get(),
                readerV2 = hasReaderV2.get(),
                hasReaderResult = hasReaderResult.get(),
                hasPageRequest = hasPageRequest.get(),
            )
            throw IOException(message, e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        failure = null
        result
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    /** Reader worker, with its HKDF page-key import reported to reader.js. */
    private fun rewrittenWorker(webRequest: WebResourceRequest, bridge: String): WebResourceResponse {
        val url = webRequest.url.toString().toHttpUrl().newBuilder().removeAllQueryParameters(bridge).build()
        val request = Request.Builder().url(url).headers(webRequest.requestHeaders.toHeaders()).build()
        client().newCall(request).execute().use { upstream ->
            // The unpatched worker would only time out without the page key
            if (!upstream.isSuccessful) throw IOException("The site's reader did not load (HTTP ${upstream.code}), try opening the chapter in WebView")
            val script = "(${asset("worker.js")})(\"$bridge\");\n" + upstream.body.string()
            return WebResourceResponse(
                "text/javascript",
                Charsets.UTF_8.name(),
                upstream.code,
                upstream.message.ifBlank { "OK" },
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(script.encodeToByteArray()),
            )
        }
    }

    // Empty stylesheet, not an error
    private fun emptyResponse() = WebResourceResponse("text/css", null, 200, "OK", null, ByteArrayInputStream(ByteArray(0)))

    private fun blockedResponse() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
}

private const val TURNSTILE_HOST = "challenges.cloudflare.com"
private val POLL_INTERVAL = 250.milliseconds
private val LOAD_INTERVAL = 2.seconds
private val BACKOFF = 5.minutes
private val CHALLENGE_WAIT = 15.seconds
private val WEBVIEW_TIMEOUT = 60.seconds

// Comment stickers, Cloudflare's prefetch hints, the favicon and the site's analytics
private val UNUSED_PATHS = listOf("/api/v1/stickers", "/cdn-cgi/speculation", "/favicon.ico", "/gmetrics/")
private val STYLE_EXTENSIONS = listOf(".css", ".woff2", ".woff", ".ttf")
private const val SOLVE_IN_WEBVIEW = "Open in WebView to pass the site's verification"

/** Only an opted-in source may use manifest/key data without an intercepted image. */
internal fun pamReaderFallbackAllowed(enabled: Boolean, wait: Duration): Boolean = enabled && wait >= 2.seconds

/** A single retry on the Soft Epsilon WebView when the encrypted worker never supplies its key. */
internal fun pamReaderNavigationRetryAllowed(
    enabled: Boolean,
    hasPage: Boolean,
    isReaderV2: Boolean,
    hasReaderResult: Boolean,
    alreadyRetried: Boolean,
    wait: Duration,
): Boolean = enabled && hasPage && isReaderV2 && !hasReaderResult && !alreadyRetried && wait >= 18.seconds

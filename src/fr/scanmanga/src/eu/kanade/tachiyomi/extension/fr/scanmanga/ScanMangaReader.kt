package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Source-local reader fallback. It captures the site's own /lel/ request rather than
 * trying to extract the now-dynamic sml/sme variables from an obfuscated script.
 *
 * Intentionally kept inside Scan-Manga: no shared WebView/core behavior is changed.
 */
internal object ScanMangaReader {
    data class LelCall(val url: String, val headers: Map<String, String>, val body: String)

    class ChallengeRequired : Exception("Cloudflare demande une vérification dans la WebView.")

    private interface WebViewFactory {
        val supportedFeatures: Array<String>
        fun createWebView(webView: WebView): InvocationHandler
    }

    private interface WebViewProvider {
        fun getProfile(): InvocationHandler
    }

    private interface WebViewProfile {
        fun clearOriginMatchedHeader(headerName: String, headerValue: String)
        fun addOriginMatchedHeader(headerName: String, headerValue: String, originRules: Set<String>)
    }

    private inline fun <reified T> InvocationHandler.boundary(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        this,
    ) as T

    /**
     * The current site returns a fake 404 when WebView injects its default
     * X-Requested-With. Chromium 142+ can override it per origin.
     */
    private fun configureReaderHeaders(view: WebView, domain: String) {
        val webViewClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.getWebViewClassLoader()
        } else {
            @SuppressLint("DiscouragedPrivateApi")
            WebView::class.java.getDeclaredMethod("getFactory")
                .apply { isAccessible = true }
                .invoke(null).javaClass.classLoader!!
        }
        val glue = Class.forName(
            "org.chromium.support_lib_glue.SupportLibReflectionUtil",
            false,
            webViewClassLoader,
        )
        val factory = (glue.getDeclaredMethod("createWebViewProviderFactory")
            .invoke(null) as InvocationHandler).boundary<WebViewFactory>()
        val features = factory.supportedFeatures.toSet()
        if ("CUSTOM_REQUEST_HEADERS" !in features || "MULTI_PROFILE" !in features) {
            throw IllegalStateException("Mettez à jour Android System WebView (Chromium 142 minimum).")
        }
        val provider = factory.createWebView(view).boundary<WebViewProvider>()
        val profile = provider.getProfile().boundary<WebViewProfile>()
        profile.clearOriginMatchedHeader("X-Requested-With", "")
        profile.addOriginMatchedHeader("X-Requested-With", "", setOf("https://*.$domain"))
    }

    private fun installReaderHook(view: WebView, script: String, domain: String) {
        // Reuse the stable document-start facility already present in the app's core.
        // It installs before the reader's own scripts, unlike onPageFinished injection.
        val glue = Class.forName("keiyoushi.webview.internal.WebViewGlueBridge")
        val instance = glue.getField("INSTANCE").get(null)
        val installed = glue.getMethod(
            "addDocumentStartJavaScript",
            WebView::class.java,
            String::class.java,
            Set::class.java,
        ).invoke(instance, view, script, setOf("https://*.$domain")) as Boolean
        if (!installed) throw IllegalStateException("Android System WebView ne permet pas l'injection au chargement.")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun capture(chapterUrl: String, chapterId: Int): LelCall {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Le lecteur Scan-Manga ne doit pas bloquer le fil principal Android."
        }

        val url = chapterUrl.toHttpUrl()
        val domain = url.topPrivateDomain() ?: throw IllegalArgumentException("Domaine invalide")
        val expectedHost = "bqj.$domain"
        val expectedPath = "/lel/$chapterId.json"
        val bridgeName = "makoffLel" + UUID.randomUUID().toString().replace("-", "")
        val latch = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        val call = AtomicReference<LelCall?>()
        val failure = AtomicReference<Exception?>()
        val viewRef = AtomicReference<WebView?>()
        val main = Handler(Looper.getMainLooper())

        fun fail(e: Exception) {
            if (completed.compareAndSet(false, true)) {
                failure.set(e)
                latch.countDown()
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun post(message: String) {
                if (completed.get()) return
                try {
                    val data = JSONObject(message)
                    val capturedUrl = data.getString("url").toHttpUrlOrNull() ?: return
                    if (capturedUrl.scheme != "https" || capturedUrl.host != expectedHost ||
                        capturedUrl.encodedPath != expectedPath
                    ) return
                    val body = data.getString("body")
                    val requestJson = JSONObject(body)
                    if (!requestJson.has("a") || !requestJson.has("b") || !requestJson.has("c")) return
                    val headerJson = data.getJSONObject("headers")
                    val headers = headerJson.keys().asSequence().associateWith { headerJson.getString(it) }
                    if (completed.compareAndSet(false, true)) {
                        call.set(LelCall(capturedUrl.toString(), headers, body))
                        latch.countDown()
                    }
                } catch (_: Exception) {
                    // Other frames and scripts cannot inject invalid reader requests.
                }
            }
        }

        val hook = """
            (function () {
                const originalFetch = window.fetch;
                window.fetch = function (input, init) {
                    const target = String((input && input.url) || input);
                    if (!target.includes("$expectedPath")) return originalFetch.apply(this, arguments);
                    const request = new Request(input, init);
                    request.clone().text().then(function (body) {
                        window.$bridgeName.post(JSON.stringify({
                            url: request.url,
                            headers: Object.fromEntries(request.headers),
                            body: body
                        }));
                    });
                    return new Promise(function () {});
                };
            })();
        """.trimIndent()

        main.post {
            if (completed.get()) return@post
            try {
                val view = WebView(eu.kanade.tachiyomi.extension.fr.scanmanga.ScanMangaReaderContext.context())
                viewRef.set(view)
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.blockNetworkImage = true
                view.addJavascriptInterface(bridge, bridgeName)
                view.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            fail(IllegalStateException(error?.description?.toString() ?: "Erreur réseau Scan-Manga"))
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                            fail(IllegalStateException("La page Scan-Manga renvoie HTTP ${errorResponse?.statusCode}."))
                        }
                    }
                }
                configureReaderHeaders(view, domain)
                installReaderHook(view, hook, domain)

                val poll = object : Runnable {
                    override fun run() {
                        if (completed.get()) return
                        view.evaluateJavascript(
                            """(function () {
                                const button = [...document.querySelectorAll('.oui-avertissement-btn')]
                                  .find(b => b.offsetParent !== null);
                                if (button) button.click();
                                return !!window._cf_chl_opt;
                            })();""",
                        ) { isChallenge ->
                            if (isChallenge == "true") fail(ChallengeRequired())
                        }
                        if (!completed.get()) main.postDelayed(this, 700L)
                    }
                }
                main.postDelayed(poll, 700L)
                view.loadUrl(chapterUrl)
            } catch (e: Exception) {
                fail(IllegalStateException("Impossible de démarrer la WebView Scan-Manga : ${e.message}", e))
            }
        }

        val finished = try {
            latch.await(40, TimeUnit.SECONDS)
        } finally {
            completed.set(true)
            main.post {
                viewRef.getAndSet(null)?.let { view ->
                    runCatching {
                        view.stopLoading()
                        view.removeJavascriptInterface(bridgeName)
                        view.destroy()
                    }
                }
            }
        }
        if (!finished) throw IllegalStateException("Scan-Manga : délai de lecture dépassé (40 s).")
        failure.get()?.let { throw it }
        return call.get() ?: throw IllegalStateException("Le lecteur Scan-Manga n'a pas transmis la requête des pages.")
    }
}

/** App context is provided by core; this wrapper avoids sharing mutable state with other sources. */
private object ScanMangaReaderContext {
    fun context() = keiyoushi.utils.applicationContext
}

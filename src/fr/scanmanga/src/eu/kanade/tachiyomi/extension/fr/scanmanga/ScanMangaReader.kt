package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import java.lang.reflect.InvocationTargetException
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

    // Chromium's reflection bridge resolves each Method's declaring class by its exact
    // fully-qualified name in the WebView APK. Use the provider's own boundary classes
    // instead of source-local copies (core already bundles some of those interfaces).
    private fun InvocationHandler.callWebViewBoundary(
        webViewClassLoader: ClassLoader,
        interfaceName: String,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        vararg arguments: Any?,
    ): Any? {
        val boundary = Class.forName(
            "org.chromium.support_lib_boundary.$interfaceName",
            false,
            webViewClassLoader,
        )
        val proxy = Proxy.newProxyInstance(webViewClassLoader, arrayOf(boundary), this)
        return try {
            boundary.getMethod(methodName, *parameterTypes).invoke(proxy, *arguments)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /**
     * The current site returns a fake 404 when WebView injects its default
     * X-Requested-With. Chromium 142+ can override it per origin.
     */
    private fun configureReaderHeaders(view: WebView, domain: String, script: String) {
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
        val factory = glue.getDeclaredMethod("createWebViewProviderFactory")
            .invoke(null) as InvocationHandler
        val supportedFeatures = factory.callWebViewBoundary(
            webViewClassLoader,
            "WebViewProviderFactoryBoundaryInterface",
            "getSupportedFeatures",
            emptyArray(),
        ) as Array<*>
        val features = supportedFeatures.filterIsInstance<String>().toSet()
        if (
            "CUSTOM_REQUEST_HEADERS" !in features ||
            "MULTI_PROFILE" !in features ||
            "DOCUMENT_START_SCRIPT:1" !in features
        ) {
            throw IllegalStateException("Mettez à jour Android System WebView (Chromium 142 minimum).")
        }
        val provider = factory.callWebViewBoundary(
            webViewClassLoader,
            "WebViewProviderFactoryBoundaryInterface",
            "createWebView",
            arrayOf(WebView::class.java),
            view,
        ) as InvocationHandler
        val profile = provider.callWebViewBoundary(
            webViewClassLoader,
            "WebViewProviderBoundaryInterface",
            "getProfile",
            emptyArray(),
        ) as InvocationHandler
        profile.callWebViewBoundary(
            webViewClassLoader,
            "ProfileBoundaryInterface",
            "clearOriginMatchedHeader",
            arrayOf(String::class.java, String::class.java),
            "X-Requested-With",
            "",
        )
        profile.callWebViewBoundary(
            webViewClassLoader,
            "ProfileBoundaryInterface",
            "addOriginMatchedHeader",
            arrayOf(String::class.java, String::class.java, Set::class.java),
            "X-Requested-With",
            "",
            setOf("https://*.$domain"),
        )
        // The hook must run before the site's own scripts build the dynamic lel request.
        provider.callWebViewBoundary(
            webViewClassLoader,
            "WebViewProviderBoundaryInterface",
            "addDocumentStartJavaScript",
            arrayOf(String::class.java, Array<String>::class.java),
            script,
            arrayOf("https://*.$domain"),
        )
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
                    ) {
                        return
                    }
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
                val view = WebView(keiyoushi.utils.applicationContext)
                viewRef.set(view)
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.blockNetworkImage = true
                // Keep viewport-triggered reader scripts functional without attaching a visible view.
                val display = Resources.getSystem().displayMetrics
                view.layoutParams = ViewGroup.LayoutParams(display.widthPixels, display.heightPixels)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(display.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(display.heightPixels, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, display.widthPixels, display.heightPixels)
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
                        if (request?.isForMainFrame == true) {
                            val status = errorResponse?.statusCode ?: 0
                            when (status) {
                                403, 503 -> fail(ChallengeRequired())
                                in 400..599 -> fail(IllegalStateException("La page Scan-Manga renvoie HTTP $status."))
                            }
                        }
                    }
                }
                configureReaderHeaders(view, domain, hook)

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

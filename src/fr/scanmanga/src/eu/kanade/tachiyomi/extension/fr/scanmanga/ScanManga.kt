package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Inflater

@Source
abstract class ScanManga :
    HttpSource(),
    ConfigurableSource {

    private val domain = baseUrl.toHttpUrl().host
    private val baseImageUrl = "https://static.${domain.removePrefix("www.")}/img/manga"
    private val baseSearchUrl = "https://bqj.${domain.removePrefix("www.")}/search/quick.json"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val stripEmptyXRequestedWith = Interceptor { chain ->
        val request = chain.request()
        val header = request.header("X-Requested-With")
        if (header != null && header.isEmpty()) {
            chain.proceed(request.newBuilder().removeHeader("X-Requested-With").build())
        } else {
            chain.proceed(request)
        }
    }

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(stripEmptyXRequestedWith)
        .build()

    // Reader-page fetches reuse the app client (cache, gzip, DoH, cookie jar, etc.) but strip
    // the host's CloudflareInterceptor — that interceptor wastes ~30 s per call trying its own
    // headless solve before throwing, blowing up our polling.
    private val readerClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" } }
            .build()
    }

    // Mobile reader requests are redirected to a separately protected host.
    private val readerHeaders by lazy {
        headers.newBuilder()
            .set("User-Agent", DESKTOP_USER_AGENT)
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("upgrade-insecure-requests", "1")
        .add(
            "accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        )
        .add("sec-fetch-site", "none")
        .add("accept-language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("X-Requested-With", "")

    private fun Element.coverUrl(): String? = sequenceOf("data-original", "data-src", "src")
        .map { absUrl(it) }
        .firstOrNull { it.toHttpUrlOrNull() != null }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/TOP-Manga-Webtoon-45.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mobileElements = document.select("#carouselTOPContainer div.top")
        val mangas = if (mobileElements.isNotEmpty()) {
            mobileElements.map { element ->
                SManga.create().apply {
                    val link = element.selectFirst("a.atop")!!

                    title = link.text()
                    setUrlWithoutDomain(link.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.coverUrl()
                }
            }
        } else {
            document.select("div.image_manga.image_listing").map { element ->
                SManga.create().apply {
                    val link = element.selectFirst("a[href]")!!
                    val img = element.selectFirst("img")

                    setUrlWithoutDomain(link.absUrl("href"))
                    title = img?.attr("title")?.takeIf { it.isNotEmpty() } ?: link.text()
                    thumbnail_url = img?.coverUrl()
                }
            }
        }

        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?po", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mobileElements = document.select("#content_news .publi")
        val mangas = if (mobileElements.isNotEmpty()) {
            mobileElements.map { element ->
                SManga.create().apply {
                    val link = element.selectFirst("a.l_manga")!!

                    title = link.text()
                    setUrlWithoutDomain(link.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.coverUrl()
                }
            }
        } else {
            document.select("div.listing:has(a.nom_manga)").map { element ->
                SManga.create().apply {
                    val link = element.selectFirst("a.nom_manga")!!
                    val img = element.selectFirst("div.logo_manga img")

                    title = link.text()
                    setUrlWithoutDomain(link.absUrl("href"))
                    thumbnail_url = img?.coverUrl()
                }
            }
        }

        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseSearchUrl
            .toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("16", null)
            .build()
            .toString()

        val newHeaders = headers.newBuilder()
            .add("Content-type", "application/json; charset=UTF-8")
            .build()

        return GET(url, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string().trim()
        if (json.isEmpty() || json == "[]") {
            return MangasPage(emptyList(), false)
        }

        return MangasPage(
            json.parseAs<MangaSearchDto>().title?.map {
                SManga.create().apply {
                    title = it.nom_match
                    setUrlWithoutDomain(it.url)
                    thumbnail_url = "$baseImageUrl/${it.image}"
                }
            } ?: emptyList(),
            false,
        )
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.main_title[itemprop=name]")?.text()?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("[itemprop=name][content]")?.attr("content")?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("h1")!!.text()
            author = document.selectFirst("div[itemprop=author]")?.parent()?.ownText()?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("li[itemprop=author]")?.text()
            description = document.selectFirst("div.titres_desc[itemprop=description]")?.text()?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("p[itemprop=description]")?.text()
            genre = document.selectFirst("div.titres_souspart span[itemprop=genre]")?.text()?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("li[itemprop=genre]")?.text()

            val statutText = document.select("div.titres_souspart")
                .firstOrNull {
                    it.ownText().contains("En cours", ignoreCase = true) ||
                        it.ownText().contains("Termin", ignoreCase = true)
                }
                ?.ownText()
                ?: document.select("div.titre_volume_manga span").text()
            status = when {
                statutText.contains("En cours", ignoreCase = true) -> SManga.ONGOING
                statutText.contains("Termin", ignoreCase = true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            thumbnail_url = document.selectFirst("div.full_img_serie img[itemprop=image]")
                ?.coverUrl()
                ?: document.select("meta[itemprop=image], meta[property=og:image]")
                    .asSequence()
                    .map { it.absUrl("content") }
                    .firstOrNull { it.toHttpUrlOrNull() != null }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mobileElements = document.select("div.chapt_m")
        return if (mobileElements.isNotEmpty()) {
            mobileElements.map { element ->
                val link = element.selectFirst("td.publimg span.i a")!!
                val chapterName = link.text()
                val extraTitle = element.selectFirst("td.publititle")?.text()

                SChapter.create().apply {
                    name = if (!extraTitle.isNullOrEmpty()) "$chapterName - $extraTitle" else chapterName
                    setChapterUrl(link.absUrl("href"))
                }
            }
        } else {
            document.select("li.chapitre").map { element ->
                val link = element.selectFirst("div.chapitre_nom a[href]")!!

                SChapter.create().apply {
                    name = link.text()
                    setChapterUrl(link.absUrl("href"))
                }
            }
        }
    }

    private fun SChapter.setChapterUrl(absoluteUrl: String) {
        if (absoluteUrl.toHttpUrl().topPrivateDomain() == baseUrl.toHttpUrl().topPrivateDomain()) {
            setUrlWithoutDomain(absoluteUrl)
        } else {
            url = absoluteUrl
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.toHttpUrlOrNull() ?: "$baseUrl${chapter.url}".toHttpUrl()
        return if (url.host == "m.scan-manga.com") {
            url.newBuilder().host(domain).build().toString()
        } else {
            url.toString()
        }
    }

    // Pages
    private fun decodeHunter(obfuscatedJs: String): String {
        val (encoded, mask, intervalStr, optionStr) = HUNTER_OBFUSCATION_REGEX.find(obfuscatedJs)?.destructured
            ?: error("Failed to match obfuscation pattern")

        val interval = intervalStr.toInt()
        val option = optionStr.toInt()
        val delimiter = mask[option]
        val tokens = encoded.split(delimiter).filter { it.isNotEmpty() }
        val reversedMap = mask.withIndex().associate { it.value to it.index }

        return buildString {
            for (token in tokens) {
                // Reverse the hashIt() operation: convert masked characters back to digits
                val digitString = token.map { c ->
                    reversedMap[c]?.toString() ?: error("Invalid masked character: $c")
                }.joinToString("")

                // Convert from base `option` to decimal
                val number = digitString.toIntOrNull(option)
                    ?: error("Failed to parse token: $digitString as base $option")

                // Reverse the shift done during encodeIt()
                val originalCharCode = number - interval

                append(originalCharCode.toChar())
            }
        }
    }

    private val multipleSpaces = Regex("""\s+""")

    private fun dataAPI(data: String, idc: Int): UrlPayload {
        if (data.contains("error")) {
            error("Received error response from data API: ${multipleSpaces.replace(data, " ").trim()}")
        }

        // Step 1: Base64 decode the input
        val compressedBytes = Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING)

        // Step 2: Inflate (zlib decompress)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val outputBuffer = ByteArray(512 * 1024)
        val decompressedLength = inflater.inflate(outputBuffer)
        inflater.end()

        val inflated = String(outputBuffer, 0, decompressedLength)

        // Step 3: Remove trailing hex string and reverse
        val hexIdc = idc.toString(16)
        val cleaned = inflated.removeSuffix(hexIdc)
        val reversed = cleaned.reversed()

        // Step 4: Base64 decode and parse JSON
        val finalJsonStr = String(Base64.decode(reversed, Base64.DEFAULT))

        return finalJsonStr.parseAs<UrlPayload>()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val context = applicationContext
        val chapterUrl = getChapterUrl(chapter)
        val chapterHost = chapterUrl.toHttpUrl()
        if (chapterHost.topPrivateDomain() != baseUrl.toHttpUrl().topPrivateDomain()) {
            return Observable.error(Exception("Ce chapitre est hébergé sur ${chapterHost.host}. Ouvrez-le dans la WebView."))
        }
        val isReader = Exception().stackTrace.any { it.className.contains("reader") }

        fun fetch(): Document? = try {
            readerClient.newCall(GET(chapterUrl, readerHeaders)).execute().use { resp ->
                resp.asJsoup().takeIf { CHAPTER_INFO_REGEX.containsMatchIn(it.html()) }
            }
        } catch (_: Exception) {
            null
        }

        var body = fetch()
        if (body == null) {
            // Cold-session path: CF refuses to issue cf_clearance to a session that's
            // never touched the host. Warm up by loading the homepage in a hidden WV,
            // then re-probe — usually clears it without ever needing WebViewActivity.
            warmupWebViewSession()
            body = fetch()
        }
        if (body == null) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("url_key", chapterUrl)
                    putExtra("source_key", id)
                    putExtra("title_key", "Résolvez le challenge Cloudflare, fermez la WebView et réouvrez le chapitre.")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                throw Exception("Résolvez le challenge Cloudflare depuis la WebView puis réouvrez le chapitre.")
            }

            for (attempt in 1..CF_MAX_POLLS) {
                Thread.sleep(CF_POLL_INTERVAL_MS)
                body = fetch()
                if (body != null) {
                    val closeIntent = Intent().apply {
                        val target = if (isReader) {
                            "eu.kanade.tachiyomi.ui.reader.ReaderActivity"
                        } else {
                            "eu.kanade.tachiyomi.ui.main.MainActivity"
                        }
                        component = ComponentName(context, target)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(closeIntent)
                    break
                }
            }
            if (body == null) {
                // WV flow exhausted itself; the warmup we did wasn't enough either.
                // Clear the gate so the next attempt warms again from scratch.
                sessionWarmedUp.set(false)
                throw Exception("Résolvez le challenge Cloudflare, fermez la WebView et réouvrez le chapitre.")
            }
        }

        return Observable.just(parsePageList(body))
    }

    private val sessionWarmedUp = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    private fun warmupWebViewSession() {
        if (!sessionWarmedUp.compareAndSet(false, true)) return

        val latch = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            val wv = WebView(applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = DESKTOP_USER_AGENT

            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Schedule teardown on the main looper directly — view.postDelayed
                    // is silently dropped because the WebView isn't attached to a window.
                    // The settle window lets CF's Turnstile beacon commit cf_clearance.
                    mainHandler.postDelayed({
                        runCatching {
                            view?.stopLoading()
                            view?.destroy()
                        }
                        latch.countDown()
                    }, WARMUP_SETTLE_MS)
                }
            }
            wv.loadUrl("$baseUrl/")
        }

        try {
            if (!latch.await(WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                sessionWarmedUp.set(false)
            }
        } catch (_: InterruptedException) {
            sessionWarmedUp.set(false)
        }
    }

    override fun pageListParse(response: Response): List<Page> = parsePageList(response.asJsoup())

    private fun parsePageList(document: org.jsoup.nodes.Document): List<Page> {
        val packedScript = document.select("script")
            .map { it.data() }
            .firstOrNull { HUNTER_OBFUSCATION_REGEX.containsMatchIn(it) }
            ?: error("Failed to find packed reader script.")
        val unpackedScript = decodeHunter(packedScript)

        val (sml) = SML_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sml parameter.")

        val (sme) = SME_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sme parameter.")

        val (chapterId) = CHAPTER_INFO_REGEX.find(document.html())?.destructured
            ?: error("Failed to extract chapter ID.")

        val availableVariables = mapOf(
            "sme" to sme,
            "sml" to sml,
            "fingerprint" to getFingerprint(),
            "chapterId" to chapterId,
            "topDomain" to (baseUrl.toHttpUrl().topPrivateDomain() ?: ""),
        )

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val documentUrl = document.baseUri().toHttpUrl()

        val requestBody = injectVariables(REQUEST_BODY, availableVariables)
        val pageListUrl = injectVariables(PAGE_LIST_URL, availableVariables)
        val requestHeaders = readerHeaders.newBuilder()
            .add("Origin", "${documentUrl.scheme}://${documentUrl.host}")
            .set("Referer", "${documentUrl.scheme}://${documentUrl.host}/")
            .set("Source", documentUrl.toString())
            .set("Token", LEL_TOKEN)
            .build()

        val pageListRequest = POST(
            url = pageListUrl,
            headers = requestHeaders,
            body = requestBody.toRequestBody(mediaType),
        )

        val lelResponse = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
            .newCall(pageListRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                        .replace(Regex("\\s+"), " ")
                        .take(240)
                    error("Unexpected error while fetching lel. HTTP ${response.code} at ${response.request.url}: $errorBody")
                }
                dataAPI(response.body.string(), chapterId.toInt())
            }

        return lelResponse.generateImageUrls().map { Page(it.first, imageUrl = it.second) }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = readerHeaders.newBuilder()
            .add("Origin", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getFingerprint(): String {
        var currentValue = preferences.getString("gpu_renderer", null)

        if (currentValue.isNullOrEmpty()) {
            val latch = CountDownLatch(1)
            var returnValue = "SUMK"

            Handler(Looper.getMainLooper()).post {
                val webView = WebView(applicationContext)
                webView.settings.javaScriptEnabled = true

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val script = """
                        (function() {
                            try {
                                const canvas = document.createElement("canvas");
                                const gl = canvas.getContext("webgl");
                                const debugInfo = gl ? gl.getExtension("WEBGL_debug_renderer_info") : null;
                                const gpu = debugInfo ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) : "IC";

                                return btoa(gpu);
                            } catch (e) {
                                return btoa("IC");
                            }
                        })();
                        """.trimIndent()

                        view?.evaluateJavascript(script) {
                            returnValue = it?.removeSurrounding("\"") ?: "SUMK"
                            view.stopLoading()
                            view.destroy()
                            latch.countDown()
                        }
                    }
                }
                webView.loadUrl("about:blank")
            }

            try {
                latch.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            }

            val decodedValue = String(Base64.decode(returnValue, Base64.DEFAULT))

            preferences.edit().putString("gpu_renderer", decodedValue).apply()
            currentValue = decodedValue
        }

        return Base64.encodeToString(
            """{"gpu":"$currentValue","connection":"cellular"}""".toByteArray(),
            Base64.NO_WRAP,
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "gpu_renderer"
            title = "Unmasked GPU renderer"
            summary =
                "Set and cache your GPU renderer string here to bypass fingerprint-based blocking. You can find your GPU renderer by visiting a site like https://www.browserleaks.com/webgl. Make sure to enter the exact string as shown on the site, without any extra spaces or characters and use Google Chrome on Android."
            setDefaultValue(null)
            dialogTitle = "GPU Renderer"
            dialogMessage =
                "Enter your GPU renderer string here. This is used to bypass blocking based on WebGL fingerprinting. You can find your GPU renderer by visiting a site like https://www.browserleaks.com/webgl using Google Chrome on Android. Make sure to enter the exact string as shown on the site, without any extra spaces or characters."

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also { screen.addPreference(it) }
    }

    private fun injectVariables(template: String, variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    companion object {
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
        private val HUNTER_OBFUSCATION_REGEX = Regex(
            """eval\s*\(\s*(?:/\*.*?\*/\s*)?function\s*\(\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*(?:,\s*[^)]+)?\)\s*\{\s*.*?\s*\}\s*\(\s*"([^"]+)"\s*,\s*\d+\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val SML_PARAM_REGEX = Regex("""sml\s*=\s*'([^']+)'""")
        private val SME_PARAM_REGEX = Regex("""sme\s*=\s*'([^']+)'""")
        private val CHAPTER_INFO_REGEX = Regex("""const\s+idc\s*=\s*(\d+)""")
        private const val PAGE_LIST_URL = "https://bqj.{topDomain}/lel/{chapterId}.json"
        private const val REQUEST_BODY = """{"a":"{sme}","b":"{sml}","c":"{fingerprint}"}"""
        private const val LEL_TOKEN = "yf"
        private const val CF_POLL_INTERVAL_MS = 5000L
        private const val CF_MAX_POLLS = 15
        private const val WARMUP_SETTLE_MS = 200L
        private const val WARMUP_TIMEOUT_SECONDS = 8L
    }
}

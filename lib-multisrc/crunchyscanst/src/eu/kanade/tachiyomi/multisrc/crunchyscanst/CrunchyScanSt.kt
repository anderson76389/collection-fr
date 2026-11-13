package eu.kanade.tachiyomi.multisrc.crunchyscanst

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

abstract class CrunchyScanSt : KeiSource() {

    private val verificationLock = Mutex()

    @Volatile
    private var verifiedAt = 0L

    // Short-lived, bounded reader cache: revisiting a page must not redo the
    // Turnstile flow and HTML request when the previously signed image is valid.
    private val pageImages = object : LinkedHashMap<String, String>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 96
    }

    private fun cachedPageImage(pageUrl: String): String? = synchronized(pageImages) {
        val imageUrl = pageImages[pageUrl] ?: return@synchronized null
        val expiry = imageUrl.toHttpUrlOrNull()?.queryParameter("e")?.toLongOrNull()
        if (expiry != null && expiry <= System.currentTimeMillis() / 1000 + EXPIRY_MARGIN_SECONDS) {
            pageImages.remove(pageUrl)
            null
        } else {
            imageUrl
        }
    }

    private fun storePageImage(pageUrl: String, imageUrl: String) = synchronized(pageImages) {
        pageImages[pageUrl] = imageUrl
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { refreshExpiredImage(it) }
        // Every reader page is its own HTML document; images are static files.
        .rateLimit(permits = 4, period = 1.seconds) { !it.encodedPath.startsWith("/cdn/") }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalogue(page, "", listOf(SortFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalogue(page, "", listOf(SortFilter().apply { state = 1 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchCatalogue(page, query, filters.filterIsInstance<UrlFilter>())

    private suspend fun fetchCatalogue(page: Int, query: String, filters: List<UrlFilter>): MangasPage {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("a[data-card-slug]").mapNotNull { it.toSManga() }
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga(): SManga? {
        val mangaTitle = selectFirst("h3")?.text()?.takeIf { it.isNotBlank() } ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(absUrl("href"))
            title = mangaTitle
            thumbnail_url = selectFirst("img")?.absUrl("data-src")?.takeIf { it.isNotBlank() }
                ?: selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = document.toSManga(manga.url),
            chapters = document.select("a[data-chapter-slug]").map { it.toSChapter() },
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        val path = "/${url.pathSegments[0]}/${url.pathSegments[1]}"
        val response = client.get(baseUrl + path, headers, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val document = response.asJsoup()
        if (document.selectFirst("a[data-chapter-slug]") == null) return null
        return document.toSManga(path)
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        val info = select("dt").associate { it.text() to it.nextElementSibling()?.text() }
        val series = selectFirst("script[type='application/ld+json']")?.data()?.let {
            runCatching { it.parseAs<LdJsonDto>().series }.getOrNull()
        }

        url = mangaUrl
        title = selectFirst("h1")!!.text()
        thumbnail_url = selectFirst("meta[property='og:image']")?.absUrl("content")
        author = info["Auteur"]
        artist = info["Artiste"]
        description = series?.description
        // The type comes with a flag emoji, e.g. "🇰🇷 Manhwa".
        genre = (listOfNotNull(info["Type"]?.substringAfter(' ')) + series?.genres.orEmpty()).joinToString()
        status = when (info["Statut"]) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            "En Pause" -> SManga.ON_HIATUS
            "Abandonné" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // Links point to the first page of the chapter: /<type>/<slug>/<chapter>/1
    private fun Element.toSChapter() = SChapter.create().apply {
        setUrlWithoutDomain(absUrl("href").substringBeforeLast('/'))
        val label = selectFirst("[data-ch-num]")!!
        name = label.text()
        // "11 mai 2025 · 161 p."
        val meta = label.parent()?.nextElementSibling()?.text()
        date_upload = DATE_FORMAT.tryParseDate(meta?.substringBefore('·')?.trim(), ZONE)
        if (name.startsWith("Chapitre")) {
            chapter_number = attr("data-chapter-slug").toFloatOrNull() ?: -1f
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}/1"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = fetchReaderPage(getChapterUrl(chapter))
        val firstImage = document.pageImageUrl()
        storePageImage(getChapterUrl(chapter), firstImage)
        val pageCount = document.selectFirst("script#reader-history")?.data()?.let {
            runCatching { it.parseAs<ReaderHistoryDto>().pages }.getOrNull()
        }?.takeIf { it in 1..1000 }
            ?: throw IllegalStateException("CrunchyScan : nombre de pages introuvable pour ce chapitre")
        return (1..pageCount).map { number ->
            val pageUrl = "$baseUrl${chapter.url}/$number"
            Page(number - 1, url = pageUrl, imageUrl = if (number == 1) firstImage.withPage(pageUrl) else null)
        }
    }

    // Each reader page holds a single image, behind a signed link only issued to a verified session.
    override suspend fun getImageUrl(page: Page): String {
        val imageUrl = cachedPageImage(page.url) ?: fetchReaderPage(page.url).pageImageUrl().also {
            storePageImage(page.url, it)
        }
        return imageUrl.withPage(page.url)
    }

    /**
     * Fetches a reader page. Without a verified session the site shows a Turnstile check instead
     * of the image: the user is then asked to pass it, the same check the site's reader shows.
     */
    private suspend fun fetchReaderPage(pageUrl: String): Document {
        val requestedAt = System.currentTimeMillis()
        val document = getReaderDocument(pageUrl)
        val siteKey = document.turnstileSiteKey() ?: return document

        verificationLock.withLock {
            // The reader loads many pages at once: verify once, for all of them.
            if (verifiedAt < requestedAt) verifySession(pageUrl, siteKey)
        }
        return getReaderDocument(pageUrl, forceNetwork = true).also {
            if (it.turnstileSiteKey() != null) throw Exception(VERIFICATION_REFUSED)
        }
    }

    private suspend fun getReaderDocument(pageUrl: String, forceNetwork: Boolean = false): Document {
        val cacheControl = if (forceNetwork) {
            CacheControl.FORCE_NETWORK
        } else {
            CacheControl.Builder().maxAge(20, TimeUnit.SECONDS).build()
        }
        return client.get(pageUrl, cacheControl = cacheControl).asJsoup()
    }

    private fun Document.turnstileSiteKey() = selectFirst(".cf-turnstile")
        ?.attr("data-sitekey")
        ?.takeIf { it.isNotBlank() }

    private suspend fun verifySession(pageUrl: String, siteKey: String) {
        val token = getTurnstileToken(pageUrl, siteKey)
        val verified = client.post("$baseUrl/api/verify-turnstile", headers.newBuilder().set("Referer", pageUrl).set("X-Requested-With", "XMLHttpRequest").build(), VerifyRequestDto(token).toJsonRequestBody())
            .parseAs<VerifyResponseDto>().ok
        if (!verified) throw Exception(VERIFICATION_REFUSED)
        verifiedAt = System.currentTimeMillis()
    }

    private fun Document.pageImageUrl() = selectFirst("img[data-page-img]")?.absUrl("src")
        ?: throw Exception("Image de la page introuvable")

    // Keeps the reader page in the fragment (never sent) so an expired link can be issued again.
    private fun String.withPage(pageUrl: String) = "$this#${pageUrl.toHttpUrl().encodedPath}"

    // Image links expire after 10 minutes, and page lists restored from the app's cache keep them,
    // so expired ones are replaced by a fresh link from their reader page.
    private fun refreshExpiredImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pagePath = request.url.fragment
        val expiry = request.url.queryParameter("e")?.toLongOrNull()
        if (pagePath == null || expiry == null || expiry > System.currentTimeMillis() / 1000 + EXPIRY_MARGIN_SECONDS) {
            return chain.proceed(request)
        }
        val readerUrl = baseUrl + pagePath
        val freshUrl = cachedPageImage(readerUrl) ?: runBlocking {
            fetchReaderPage(readerUrl).pageImageUrl().also { storePageImage(readerUrl, it) }
        }
        return chain.proceed(request.newBuilder().url(freshUrl).build())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    private companion object {
        const val VERIFICATION_REFUSED = "Vérification refusée, réessayez"
        const val EXPIRY_MARGIN_SECONDS = 30

        val ZONE: ZoneId = ZoneId.of("Europe/Paris")
        val DATE_FORMAT = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMMM yyyy")
            .toFormatter(Locale.FRENCH)
    }
}

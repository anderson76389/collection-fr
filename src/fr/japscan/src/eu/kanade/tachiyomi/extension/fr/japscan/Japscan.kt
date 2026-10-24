package eu.kanade.tachiyomi.extension.fr.japscan

import android.webkit.CookieManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Japscan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 2.seconds)

    override fun getHomeUrl() = "$baseUrl/catalogue?sort=populaire"

    override suspend fun getPopularManga(page: Int) = getCatalogue(page, "sort" to "populaire")

    override suspend fun getLatestUpdates(page: Int) = getCatalogue(page, "sort" to "nouveautes")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        return getCatalogue(page, "q" to query.trim())
    }

    private suspend fun getCatalogue(page: Int, param: Pair<String, String>): MangasPage {
        val catalogueUrl = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter(param.first, param.second)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(catalogueUrl).asJsoup()
        val mangas = document.select("a[data-card-slug]").map { element ->
            SManga.create().apply {
                url = japscanMangaPath(element.attr("href"))
                title = element.selectFirst("h3")?.text() ?: element.selectFirst("img")!!.attr("alt")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("nav[aria-label=Pagination] a:contains(Suivant)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // Library entries created on japscan.foo must remain associated with source ID 11.
        if (url.host !in JAPSCAN_DOMAINS) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] !in CHAPTER_PATH_TYPES) return null
        val mangaUrl = japscanMangaPath(url.encodedPath)
        val document = client.get(baseUrl + mangaUrl).asJsoup()
        verifySiteAccess(document)
        return parseMangaDetails(document).apply {
            this.url = mangaUrl
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        verifySiteAccess(document)
        val details = parseMangaDetails(document).apply {
            url = manga.url
            title = manga.title
        }
        val chapterList = document.select("ol[data-chapter-list] a[data-chapter-slug]").map(::parseChapter)
        return SMangaUpdate(details, chapterList)
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("main h1")?.text()
            ?: throw IOException("Japscan : fiche non trouvée sur japscan.st.")
        thumbnail_url = document.selectFirst("main img[alt]:not([alt=''])")?.absUrl("src")
        val info = document.select("main dl > div").mapNotNull { row ->
            val key = row.selectFirst("dt")?.text() ?: return@mapNotNull null
            val value = row.selectFirst("dd")?.text() ?: return@mapNotNull null
            key to value
        }.toMap()
        author = info["Auteur"]
        status = parseStatus(info["Statut"].orEmpty())
        genre = (listOfNotNull(info["Type"]?.substringAfter(' ')) + document.select("main a[href*='genre=']").map { it.text() })
            .joinToString()
        description = document.selectFirst("main h2:contains(Synopsis) + p")?.text()
    }

    private fun parseStatus(status: String) = status.lowercase().let {
        when {
            it.contains("en cours") -> SManga.ONGOING
            it.contains("terminé") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapter(element: Element) = SChapter.create().apply {
        // Chapter links point at the reader's first page (/manga/<slug>/<n>/1); keep the
        // pre-redesign /manga/<slug>/<n>/ form so existing library entries keep their read state.
        url = japscanChapterPath(element.attr("href"))
        name = element.selectFirst("[data-ch-num]")!!.text()
        chapter_number = CHAPTER_NUM_REGEX.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("span.block.text-muted")?.text()?.substringBefore(" · "), PARIS)
    }

    private val pageListMutex = Mutex()

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListMutex.withLock {
        // Japscan's redesigned site serves one page per URL; the legacy canvas
        // descrambler is entirely obsolete and must not run on japscan.st.
        val firstPageUrl = baseUrl + japscanChapterPath(chapter.url).trimEnd('/') + "/1"
        val document = client.get(firstPageUrl, CacheControl.FORCE_NETWORK).asJsoup()
        verifySiteAccess(document)
        val pages = document.select("select[data-page-select] option").mapIndexedNotNull { index, option ->
            option.absUrl("value").takeIf(String::isNotBlank)?.let { Page(index, url = it) }
        }
        if (pages.isNotEmpty()) return@withLock pages
        // For a one-page chapter the selector may be absent.
        if (document.selectFirst("img[data-page-img]") != null) return@withLock listOf(Page(0, url = firstPageUrl))
        throw IOException(
            "Japscan : lecteur indisponible sur japscan.st (aucune page trouvée). " +
                "Ouvrez ce chapitre dans la WebView Mihon pour vérifier son affichage.",
        )
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url, CacheControl.FORCE_NETWORK).asJsoup()
        verifySiteAccess(document)
        val image = document.selectFirst("img[data-page-img]")
        return image?.absUrl("data-src")?.takeIf(String::isNotBlank)
            ?: image?.absUrl("src")?.takeIf(String::isNotBlank)
            ?: throw IOException("Japscan : image introuvable pour cette page sur japscan.st.")
    }

    override fun imageRequest(page: Page): Request = Request.Builder().url(page.imageUrl!!)
        .headers(headers.newBuilder().set("Referer", page.url).build())
        .apply {
            // The CDN may require the browser session attached to its own host.
            // Never send japscan.st cookies to a different image domain.
            runCatching { CookieManager.getInstance().getCookie(page.imageUrl!!) }
                .getOrNull()?.takeIf(String::isNotBlank)?.let { header("Cookie", it) }
        }
        .build()

    private fun verifySiteAccess(document: Document) {
        val title = document.title().lowercase(Locale.ROOT)
        if (document.selectFirst(".cf-turnstile") != null ||
            document.selectFirst("[class*=cf-challenge]") != null ||
            document.outerHtml().contains("cf-turnstile") ||
            title.contains("just a moment") ||
            title.contains("access denied") ||
            title.contains("attention required")
        ) {
            throw IOException(
                "Japscan : vérification Cloudflare requise sur japscan.st. " +
                    "Ouvrez le chapitre dans la WebView Mihon, terminez la vérification " +
                    "si elle apparaît, puis réessayez.",
            )
        }
    }

    companion object {
        private val CHAPTER_PATH_TYPES = setOf("manga", "manhua", "manhwa", "bd", "comic", "comics", "global-manga")
        private val JAPSCAN_DOMAINS = setOf("japscan.st", "www.japscan.st", "japscan.foo", "www.japscan.foo")
        private val CHAPTER_NUM_REGEX = Regex("""(?i)chapitre\\s+([\\d.]+)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        private val PARIS = ZoneId.of("Europe/Paris")
    }
}

// Keep the old type/slug URL convention so Mihon source ID 11 can match
// titles and chapters already stored in the user's library.
internal fun japscanMangaPath(href: String): String {
    val segments = japscanPathSegments(href)
    require(segments.size >= 2) { "Lien de série Japscan invalide : $href" }
    return "/${segments[0]}/${segments[1]}/"
}

internal fun japscanChapterPath(href: String): String {
    val segments = japscanPathSegments(href)
    require(segments.size >= 3) { "Lien de chapitre Japscan invalide : $href" }
    return "/${segments[0]}/${segments[1]}/${segments[2]}/"
}

private fun japscanPathSegments(href: String): List<String> {
    val path = runCatching { URI(href).rawPath }.getOrNull() ?: href.substringBefore('?')
    return path.split('/').filter(String::isNotBlank)
}

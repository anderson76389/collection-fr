package eu.kanade.tachiyomi.extension.fr.mangakawaii

import android.os.SystemClock
import android.util.LruCache
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaKawaii : KeiSource() {

    private val pageCursors = LruCache<String, MutableMap<Int, Int>>(8)

    private val readerDocuments = LruCache<String, CachedReader>(3)

    @Volatile
    private var browserUserAgent: String? = null

    override fun Headers.Builder.configureHeaders() = apply {
        set("Referer", "$baseUrl/")
        set("Accept", HTML_ACCEPT)
        set("Accept-Language", "fr-FR,fr;q=0.9")
        browserUserAgent?.let { set("User-Agent", it) }
    }

    private suspend fun document(url: String, requestHeaders: Headers = headers): Document {
        val response = client.get(url, requestHeaders, cacheControl = CacheControl.FORCE_NETWORK, ensureSuccess = false)
        val code = response.code
        if (response.isSuccessful) return response.asJsoup()
        response.close()
        if (code != 403) throw IOException("MangaKawaii : erreur HTTP $code.")
        return browserDocument(url)
    }

    private suspend fun browserDocument(url: String): Document {
        val bridge = "mk" + UUID.randomUUID().toString().replace("-", "")
        val script = """
            (() => {
                if (document.querySelector('[x-data*="imgs"], #chapitres, a.mk-card[href*="/manga/"], div.ch-row')) {
                    window.$bridge.post(document.documentElement.outerHTML);
                }
            })();
        """.trimIndent()
        val html = try {
            runWebView<String>(timeout = 30.seconds) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockImages = true
                browserUserAgent = userAgent
                val empty = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                interceptRequest { request ->
                    val host = request.url.host.orEmpty()
                    if (host == "mangakawaii.fr" || host.endsWith(".mangakawaii.fr") || host == "challenges.cloudflare.com") null else empty
                }
                jsBridge(bridge) { resolve(it) }
                onPageFinished { evaluateJs(script) }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException("MangaKawaii : ${error.description}"))
                }
                poll(500.milliseconds) { evaluateJs(script) }
                loadUrl(url, mapOf("Referer" to "$baseUrl/", "Accept" to HTML_ACCEPT))
            }
        } catch (e: WebViewTimeoutException) {
            throw IOException("MangaKawaii refuse l’accès. Ouvrez la source dans la WebView, validez la vérification du site puis réessayez.", e)
        }
        return Jsoup.parse(html, url)
    }

    private suspend fun readerDocument(url: String): Document {
        readerDocuments.get(url)?.takeIf { SystemClock.elapsedRealtime() - it.fetchedAt < 20_000 }
            ?.let { return it.document }
        return document(url).also {
            if (it.selectFirst("[x-data*=imgs]") != null) {
                readerDocuments.put(url, CachedReader(it, SystemClock.elapsedRealtime()))
            }
        }
    }

    private class CachedReader(val document: Document, val fetchedAt: Long)

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return cataloguePage(url, page)
    }

    // ============================== Latest ==============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "last_updated")
            .addQueryParameter("dir", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        return cataloguePage(url, page)
    }

    // ============================== Search ==============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // The global search omits licence badges; the manga catalogue exposes them.
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return cataloguePage(url, page)
    }

    private suspend fun cataloguePage(url: HttpUrl, page: Int): MangasPage {
        val key = url.newBuilder().removeAllQueryParameters("page").build().toString()
        val cursors = if (page == 1) {
            mutableMapOf(1 to 1).also { pageCursors.put(key, it) }
        } else {
            pageCursors.get(key) ?: mutableMapOf<Int, Int>().also { pageCursors.put(key, it) }
        }
        var upstreamPage = synchronized(cursors) { cursors[page] ?: page }
        while (true) {
            val document = document(url.newBuilder().setQueryParameter("page", upstreamPage.toString()).build().toString())
            val mangas = parseMangaCards(document)
            val hasNext = hasNextPage(document, upstreamPage)
            if (mangas.isNotEmpty() || !hasNext) {
                synchronized(cursors) { cursors[page + 1] = upstreamPage + 1 }
                return MangasPage(mangas, hasNext)
            }
            upstreamPage++
        }
    }

    private fun parseMangaCards(document: Document): List<SManga> = document.select("""a.mk-card[href*="/manga/"]""").mapNotNull { element ->
        if (element.select("span").any { it.ownText().equals("LICENCIÉ", ignoreCase = true) }) return@mapNotNull null
        val img = element.selectFirst("img")
        val title = element.selectFirst("p.mk-display")?.text() ?: img?.attr("alt")
        if (title.isNullOrBlank()) return@mapNotNull null
        SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(element.absUrl("href"))
            thumbnail_url = img?.absUrl("src")?.takeIf { it.isNotBlank() }
        }
    }

    private fun hasNextPage(document: Document, page: Int): Boolean {
        val nextPage = Regex("[?&]page=${page + 1}(?:[&#]|$)")
        return document.select("""nav[aria-label="Pagination"] a[href]""")
            .any { nextPage.containsMatchIn(it.attr("href")) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.firstOrNull() != "manga" || segments.size < 2) return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/manga/${segments[1]}")
        }
        getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }.getOrNull()

    // ======================= Details and Chapters =======================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = document(getMangaUrl(manga))
        if (document.select("span[title]").any { it.attr("title").startsWith("Licencié", ignoreCase = true) } ||
            document.select("[data-ch-list]").text().contains("Licencié", ignoreCase = true)
        ) {
            throw IOException("Cette œuvre licenciée est désactivée par MangaKawaii et exclue de cette extension.")
        }
        val updatedManga = parseDetails(document).apply {
            url = manga.url
            if (title.isEmpty()) {
                title = manga.title
            }
        }
        val chapterList = if (fetchChapters) parseChapters(document, chapters) else chapters
        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text().orEmpty()
        thumbnail_url = document.selectFirst("img.shadow-2xl")?.absUrl("src")?.takeIf { it.isNotBlank() }
        description = document.selectFirst("[x-ref=desc]")?.text()

        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        var inArtists = false
        document.selectFirst("p:has(i.fa-pen-nib)")?.children()?.forEach { node ->
            when {
                node.tagName() == "i" && node.hasClass("fa-paintbrush") -> inArtists = true
                node.tagName() == "a" -> (if (inArtists) artists else authors).add(node.text())
            }
        }
        author = authors.distinct().joinToString()
        artist = artists.distinct().joinToString()
        genre = document.select("a[href*=genres]").eachText().distinct().joinToString()

        val info = document.select("details p")
        status = when (info.firstOrNull()?.text()?.substringAfterLast("·")?.trim()?.lowercase(Locale.ROOT)) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val altNames = info.getOrNull(1)?.text().orEmpty()
        if (altNames.isNotEmpty()) {
            description = buildString {
                if (!description.isNullOrBlank()) {
                    append(description)
                    append("\n\n")
                }
                append("Autres titres : ")
                append(altNames)
            }
        }
    }

    private suspend fun parseChapters(document: Document, knownChapters: List<SChapter>): List<SChapter> {
        val chaptersUrl = document.selectFirst("[data-url*=chapitres]")?.attr("abs:data-url")
            ?.takeIf { it.isNotBlank() }
            ?: document.location().toHttpUrl().newBuilder()
                .addPathSegment("chapitres")
                .build()
                .toString()

        val chapters = mutableListOf<SChapter>()
        parseChapterRows(document.select("div.ch-row"), chapters)
        if (chapters.isEmpty() && document.select("[data-ch-list]").text().contains("Licencié", true)) {
            throw IOException("Les chapitres de cette œuvre sont désactivés par MangaKawaii (œuvre licenciée).")
        }
        var nextPage = document.selectFirst("[data-ch-next]")?.attr("data-ch-next")?.toIntOrNull()
        if (nextPage != null && chapters.isNotEmpty()) {
            // The reader's chapter picker contains the complete index without the protected AJAX paging route.
            val reader = readerDocument(getChapterUrl(chapters.first()))
            val state = decodeReaderState(reader)
            val index = CHAPTER_INDEX_REGEX.find(state)?.groupValues?.get(1)
                ?.parseAs<List<ReaderChapter>>()?.mapNotNull { it.toChapter(document.location()) }.orEmpty()
            val initial = chapters.associateBy { it.url }
            if (index.size >= initial.size && index.map { it.url }.toSet().containsAll(initial.keys)) {
                val known = knownChapters.associateBy { it.url }
                return index.distinctBy { it.url }.map { initial[it.url] ?: it }.onEach {
                    if (it.date_upload == 0L) it.date_upload = known[it.url]?.date_upload ?: 0L
                }
            }
        }
        val chaptersBase = chaptersUrl.toHttpUrl()
        val ajaxHeaders = headers.newBuilder()
            .set("X-Requested-With", "fetch")
            .set("Referer", document.location())
            .build()
        while (nextPage != null) {
            val currentPage = nextPage
            val pageUrl = chaptersBase.newBuilder()
                .setQueryParameter("page", currentPage.toString())
                .build()
            val fragment = document(pageUrl.toString(), ajaxHeaders)
            parseChapterRows(fragment.select("div.ch-row"), chapters)
            nextPage = fragment.selectFirst("[data-ch-next]")?.attr("data-ch-next")?.toIntOrNull()
                ?.takeIf { it > currentPage }
        }
        return chapters.distinctBy { it.url }
    }

    private fun parseChapterRows(rows: Elements, out: MutableList<SChapter>) {
        rows.mapNotNullTo(out) { element ->
            val link = element.selectFirst("a.ch-num") ?: return@mapNotNullTo null
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                CHAPTER_NUMBER_REGEX.find(name)?.let {
                    chapter_number = it.groupValues[1].toFloat()
                }
                date_upload = parseRelativeDate(element.selectFirst("p")?.text().orEmpty())
            }
        }
    }

    private fun parseRelativeDate(text: String): Long {
        val clean = text.trim().lowercase(Locale.ROOT)
        if ("instant" in clean) return System.currentTimeMillis()
        val match = RELATIVE_DATE_REGEX.find(clean) ?: return 0L
        val value = match.groupValues[1].toIntOrNull() ?: return 0L
        return Calendar.getInstance().apply {
            when (match.groupValues[2].trimEnd('.')) {
                "s", "sec", "seconde", "secondes" -> add(Calendar.SECOND, -value)
                "min", "minute", "minutes" -> add(Calendar.MINUTE, -value)
                "h", "heure", "heures" -> add(Calendar.HOUR_OF_DAY, -value)
                "j", "jour", "jours" -> add(Calendar.DATE, -value)
                "sem", "semaine", "semaines" -> add(Calendar.DATE, -value * 7)
                "mois" -> add(Calendar.MONTH, -value)
                "an", "ans", "année", "années", "annee", "annees" -> add(Calendar.YEAR, -value)
                else -> return 0L
            }
        }.timeInMillis
    }

    // ============================== Pages ==============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = readerDocument(chapterUrl)
        val state = decodeReaderState(document)
        val imagesJson = IMAGES_REGEX.find(state)?.groupValues?.get(1).orEmpty()
        val urls = runCatching { "[$imagesJson]".parseAs<List<String>>() }
            .getOrDefault(emptyList())
            .filter { "__mk_trap__" !in it }
            .distinct()
        if (urls.isNotEmpty()) {
            return urls.mapIndexed { i, url -> Page(i, chapterUrl, url) }
        }
        val fallback = document.select("img[id^=pg-]").mapNotNull { img ->
            img.absUrl("src").takeIf { it.isNotBlank() && "__mk_trap__" !in it }
        }.distinct()
        if (fallback.isEmpty()) throw IOException("MangaKawaii ne renvoie aucune page lisible pour ce chapitre.")
        return fallback.mapIndexed { index, url -> Page(index, chapterUrl, url) }
    }

    override fun imageRequest(page: Page) = GET(
        page.imageUrl!!,
        headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "same-site")
            .set("Referer", page.url.ifBlank { "$baseUrl/" })
            .build(),
    )

    private fun decodeReaderState(document: Document): String {
        var state = document.selectFirst("[x-data*=imgs]")?.attr("x-data").orEmpty()
        var previous: String
        do {
            previous = state
            state = state
                .replace("\\\\", "\\")
                .replace("\\u0022", "\"")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
        } while (state != previous)
        return state
    }

    @Serializable
    private class ReaderChapter(val n: String, val url: String) {
        fun toChapter(mangaUrl: String): SChapter? {
            val base = mangaUrl.toHttpUrl()
            val target = base.resolve(url) ?: return null
            if (target.host != base.host || !target.encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/")) return null
            return SChapter.create().apply {
                setUrlWithoutDomain(target.toString())
                name = "Ch. $n"
                chapter_number = n.toFloatOrNull() ?: -1f
            }
        }
    }

    companion object {
        private val RELATIVE_DATE_REGEX = Regex("""il y a (\d+)\s*([a-zéû.]+)""")
        private val CHAPTER_NUMBER_REGEX = Regex("""Ch\.\s*(\d+(?:\.\d+)?)""")
        private const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private val CHAPTER_INDEX_REGEX = Regex(""""chapters":\s*(\[.*?])""", RegexOption.DOT_MATCHES_ALL)
        private val IMAGES_REGEX = Regex(""""imgs":\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
    }
}

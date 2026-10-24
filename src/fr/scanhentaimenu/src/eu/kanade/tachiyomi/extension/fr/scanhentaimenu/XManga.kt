package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

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
import keiyoushi.utils.runWebView
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// October 2026: X-Manga migrated away from Madara/WordPress.
// Preserve /manga/<slug>/ for existing libraries, use /read/<uuid>/ for chapters.
@Source
abstract class XManga : KeiSource() {
    override fun getHomeUrl() = "$baseUrl/"

    override suspend fun getPopularManga(page: Int): MangasPage = browseCatalogue(page, "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browseCatalogue(page, "")

    private suspend fun browseCatalogue(page: Int, query: String): MangasPage {
        if (page !in 1..100) return MangasPage(emptyList(), false)
        val document = try {
            browserDocument("$baseUrl/catalogue/", "catalogue", page, query)
        } catch (error: Exception) {
            // Only the first page can safely fall back to the homepage. An
            // exhausted or blocked catalogue must not duplicate page 1 forever.
            if (page != 1 || query.isNotBlank()) throw error
            client.get("$baseUrl/").asJsoup()
        }
        val works = xMangaCards(document).let { entries ->
            if (query.isBlank()) entries else entries.filter { it.title.contains(query, ignoreCase = true) }
        }
        if (works.isEmpty() && page == 1 && query.isBlank()) {
            throw IOException("X-Manga : catalogue non chargé dans la WebView.")
        }
        return MangasPage(works, works.isNotEmpty() && page < 100)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        return browseCatalogue(page, query)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/manga/")) return null
        return xMangaDetails(client.get(url.toString()).asJsoup(), url.encodedPath)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga)
        val document = client.get(url).asJsoup()
        val detail = if (fetchDetails) xMangaDetails(document, manga.url) else manga
        var found = if (fetchChapters) xMangaChapters(document) else chapters
        // The server provides the first 12 chapter links. More are client-rendered.
        if (fetchChapters && found.size < xMangaChapterCount(document)) {
            val browserChapters = runCatching { xMangaChapters(browserDocument(url, "read")) }
                .getOrDefault(emptyList())
            if (browserChapters.size > found.size) found = browserChapters
        }
        if (fetchChapters && found.isEmpty()) {
            throw IOException("X-Manga : liste de chapitres introuvable. Vérifiez la fiche dans la WebView.")
        }
        return SMangaUpdate(detail, found)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val direct = runCatching { xMangaReaderImages(client.get(chapterUrl).asJsoup()) }
            .getOrDefault(emptyList())
        val images = direct.ifEmpty {
            val raw = try {
                runWebView<String>(timeout = 65.seconds) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockImages = false
                    userAgent = headers["User-Agent"]!!
                    jsBridge("xmangapages") { payload ->
                        if (payload.startsWith("[")) resolve(payload)
                    }
                    onReceivedError { request, error ->
                        if (request.isForMainFrame) {
                            reject(IOException("X-Manga : page du lecteur inaccessible : " + error.description))
                        }
                    }
                    poll(700.milliseconds) {
                        evaluateJs(
                            """
                            (function() {
                                // The current site populates page images after JavaScript
                                // hydration. Do not resolve on the site's logo or a single
                                // prematurely loaded image while the page is still scrolling.
                                const main = document.querySelector('main') || document.body;
                                if (!/chapitre|chapter/i.test(main.innerText || '')) return 'reader-not-ready';
                                const images = Array.from(main.querySelectorAll('img'));
                                window.__xmPages = window.__xmPages || [];
                                for (const img of images) {
                                    img.loading = 'eager';
                                    const url = img.currentSrc || img.getAttribute('data-src') || img.getAttribute('src') || '';
                                    if (!/^https?:\/\//i.test(url)) continue;
                                    if (/avatar|logo|cover|thumb|banner|poster|placeholder/i.test(url + ' ' + img.alt)) continue;
                                    if (!img.complete || img.naturalWidth < 250 || img.naturalHeight < 250) continue;
                                    if (!window.__xmPages.includes(url)) window.__xmPages.push(url);
                                }
                                const next = Math.min(document.documentElement.scrollHeight, window.scrollY + innerHeight * 1.6);
                                window.scrollTo(0, next);
                                const bottom = window.scrollY + innerHeight >= document.documentElement.scrollHeight - 16;
                                const now = Date.now();
                                if (!bottom) {
                                    window.__xmAtBottom = 0;
                                    return 'scrolling';
                                }
                                if (!window.__xmAtBottom) window.__xmAtBottom = now;
                                if (window.__xmPages.length > 0 && now - window.__xmAtBottom > 3000) {
                                    window.xmangapages.post(JSON.stringify(window.__xmPages));
                                    return 'done';
                                }
                                return 'waiting-images';

                            })()
                            """.trimIndent(),
                        )
                    }
                    loadUrl(chapterUrl)
                }
            } catch (error: WebViewTimeoutException) {
                throw IOException("X-Manga : aucune image chargée par le lecteur JavaScript. Vérifiez dans la WebView.", error)
            }
            JSONArray(raw).let { array -> (0 until array.length()).map(array::getString) }
        }
        if (images.isEmpty()) throw IOException("X-Manga : aucune image disponible dans ce chapitre.")
        return images.distinct().mapIndexed { idx, image -> Page(idx, url = chapterUrl, imageUrl = image) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url.takeIf { it.startsWith(baseUrl) } ?: "$baseUrl/")
        .build()

    private suspend fun browserDocument(url: String, type: String, page: Int = 1, query: String = ""): Document {
        val safeQuery = org.json.JSONObject.quote(query)
        val html = runWebView<String>(timeout = 85.seconds) {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockImages = true
            userAgent = headers["User-Agent"]!!
            jsBridge("xmangadom") { payload ->
                if (payload.contains(if (type == "read") "/read/" else "/manga/")) resolve(payload)
            }
            onReceivedError { request, error ->
                if (request.isForMainFrame) reject(IOException("X-Manga : " + error.description))
            }
            poll(750.milliseconds) {
                evaluateJs(
                    """
                    (function() {
                        const type = '$type', target = $page, search = $safeQuery;
                        const text = document.body?.innerText || '';
                        if (type === 'read') {
                            const more = Array.from(document.querySelectorAll('button'))
                                .find(b => /afficher.*chapitres.*plus/i.test(b.textContent || ''));
                            if (more && Date.now() - (window.__xmLastClick || 0) > 400) {
                                more.click(); window.__xmLastClick = Date.now(); return 'more';
                            }
                        }
                        if (type === 'catalogue') {
                            if (/chargement du catalogue/i.test(text)) return 'loading';
                            const input = document.querySelector('input[placeholder*="Titre"], input[placeholder*="titre"]');
                            if (search && input && !window.__xmQueryEntered) {
                                const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                                setter.call(input, search);
                                input.dispatchEvent(new Event('input', {bubbles: true}));
                                window.__xmQueryEntered = true;
                                window.__xmLastClick = Date.now(); return 'search';
                            }
                            if (target > 1 && !window.__xmPageSelected) {
                                const btns = Array.from(document.querySelectorAll('button,a'));
                                const button = btns.find(b => b.textContent.trim() === String(target) &&
                                    b.getBoundingClientRect().width > 0);
                                if (!button) return 'no-page-button';
                                button.click(); window.__xmPageSelected = true;
                                window.__xmLastClick = Date.now(); return 'page';
                            }
                        }
                        const selector = type === 'read' ? 'a[href*="/read/"]' : 'main a[href*="/manga/"]';
                        const links = Array.from(document.querySelectorAll(selector));
                        const unique = new Set(links.map(a => a.pathname).filter(p => p && p.startsWith(type === 'read' ? '/read/' : '/manga/')));
                        const key = [...unique].join('|');
                        if (window.__xmLastList !== key) {
                            window.__xmLastList = key;
                            window.__xmListStableAt = Date.now();
                        }
                        if (unique.size > 0 && Date.now() - (window.__xmListStableAt || 0) > 1700 &&
                            Date.now() - (window.__xmLastClick || 0) > 1700) {
                            window.xmangadom.post(document.documentElement.outerHTML);
                            return 'sent';
                        }
                        return 'waiting';
                    })()
                    """.trimIndent(),
                )
            }
            loadUrl(url)
        }
        return Jsoup.parse(html, url)
    }
}

internal fun xMangaCards(document: Document): List<SManga> = document.select("a[href*=/manga/]").mapNotNull { link ->
    val href = link.absUrl("href")
    val uri = runCatching { href.toHttpUrl() }.getOrNull() ?: return@mapNotNull null
    if (!uri.encodedPath.startsWith("/manga/") || uri.pathSegments.count { it.isNotBlank() } != 2) {
        return@mapNotNull null
    }
    val title = link.selectFirst("h2,h3,h4")?.text()?.trim()
        ?: link.selectFirst("img[alt]")?.attr("alt")?.removePrefix("Couverture de ")?.trim()
        ?: link.text().trim()
    if (title.isBlank()) return@mapNotNull null
    SManga.create().apply {
        url = uri.encodedPath
        this.title = title.removePrefix("MANHWA ").removePrefix("PORNHWA ")
            .removePrefix("MANGA ").removePrefix("DOUJINSHI ")
        thumbnail_url = link.selectFirst("img")?.let { img ->
            sequenceOf("data-src", "src").map(img::absUrl).firstOrNull { it.startsWith("http") }
        }
    }
}.distinctBy { it.url }

internal fun xMangaDetails(document: Document, path: String): SManga = SManga.create().apply {
    url = path
    title = document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
        ?: throw IOException("X-Manga : fiche sans titre (ancienne URL ou page 404).")
    thumbnail_url = document.selectFirst("img[alt^=Couverture]")?.absUrl("src")
        ?: document.selectFirst("meta[property=og:image]")?.absUrl("content")
    description = document.selectFirst("meta[name=description]")?.attr("content")
    val content = document.body().text()
    status = when {
        content.contains("Terminé", true) -> SManga.COMPLETED
        content.contains("En cours", true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
}

internal fun xMangaChapterCount(document: Document): Int = Regex("""(\d+)\s+chapitres""", RegexOption.IGNORE_CASE)
    .find(document.body().text())?.groupValues?.get(1)?.toIntOrNull() ?: 0

internal fun xMangaChapters(document: Document): List<SChapter> = document.select("a[href*=/read/]").mapNotNull { link ->
    val uri = runCatching { link.absUrl("href").toHttpUrl() }.getOrNull() ?: return@mapNotNull null
    if (!uri.encodedPath.startsWith("/read/")) return@mapNotNull null
    val name = Regex("""(?i)chapitre\s*([\d.]+)""").find(link.text())
    SChapter.create().apply {
        url = uri.encodedPath
        this.name = name?.value ?: link.text().ifBlank { "Oneshot" }
        chapter_number = name?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }
}.distinctBy { it.url }

internal fun xMangaReaderImages(document: Document): List<String> = document.select("main img[src], article img[src], [class*=reader] img[src]")
    .mapNotNull { it.absUrl("src").takeIf { url -> url.startsWith("http") && !url.contains("placeholder") } }
    .distinct()

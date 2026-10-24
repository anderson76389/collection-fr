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
    // Preserve the image request headers seen by the successful WebView
    // session so Mihon can fetch the same CDN images with matching headers.
    private val imageHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

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
        // HTML delivered before hydration contains placeholders and decorative
        // <img> tags. Never treat those as pages: capture the actual loaded
        // reader images in the browser that owns the chapter session.
        val lastState = java.util.concurrent.atomic.AtomicReference("attente du lecteur")
        val raw = try {
            runWebView<String>(timeout = 95.seconds) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockImages = false
                userAgent = headers["User-Agent"]!!
                jsBridge("xmangapages") { payload ->
                    if (payload.startsWith("[")) {
                        resolve(payload)
                    } else if (payload.startsWith("{")) {
                        runCatching { org.json.JSONObject(payload).optString("stage") }
                            .getOrNull()?.takeIf(String::isNotBlank)?.let(lastState::set)
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) {
                        reject(IOException("X-Manga : chapitre inaccessible dans la WebView : " + error.description))
                    }
                }
                interceptRequest { request ->
                    val url = request.url.toString()
                    if (url.startsWith("https://") && (
                            url.contains(".webp") || url.contains(".jpg") ||
                                url.contains(".jpeg") || url.contains(".png") || url.contains(".avif")
                        )
                    ) {
                        imageHeaders[url] = request.requestHeaders
                    }
                    null
                }
                poll(650.milliseconds) {
                    evaluateJs(
                        """
                        (function() {
                            const container = document.querySelector('main') || document.body;
                            if (!container) return 'waiting';
                            const heading = container.querySelector('h1');
                            const readerReady = /chapitre|chapter/i.test(container.innerText || '') || !!heading;
                            if (!readerReady) return 'waiting';
                            window.__xmPages = window.__xmPages || [];
                            const images = Array.from(container.querySelectorAll('img'));
                            let pending = 0, candidates = 0;
                            for (const img of images) {
                                img.loading = 'eager';
                                // Some chapters keep a loading placeholder in src
                                // and the real page address in data-src or data-original.
                                const raw = img.getAttribute('data-src') || img.getAttribute('data-original') ||
                                    img.currentSrc || img.getAttribute('src') || '';
                                if (!raw || raw.startsWith('data:')) continue;
                                let url;
                                try { url = new URL(raw, location.href).href; } catch (e) { continue; }
                                if (!/^https?:\/\//i.test(url)) continue;
                                if (/avatar|logo|cover|thumb|banner|poster|placeholder|backdrop/i.test(url + ' ' + img.alt)) continue;
                                if (img.naturalWidth < 220 || img.naturalHeight < 220 || !img.complete) {
                                    pending++;
                                    // Calling scrollIntoView on every unloaded image jumps to
                                    // the last page and starves the lazy-loaded middle pages.
                                    continue;
                                }
                                candidates++;
                                if (!window.__xmPages.includes(url)) window.__xmPages.push(url);
                            }
                            const step = Math.max(320, innerHeight * .75);
                            window.__xmScanY = Math.min(document.documentElement.scrollHeight, (window.__xmScanY || 0) + step);
                            window.scrollTo(0, window.__xmScanY);
                            const atEnd = window.__xmScanY + innerHeight >= document.documentElement.scrollHeight - 10;
                            if (!atEnd) window.__xmFinishedAt = 0;
                            else if (!window.__xmFinishedAt) window.__xmFinishedAt = Date.now();
                            const now = Date.now();
                            if (window.__xmKnownCount !== window.__xmPages.length) {
                                window.__xmKnownCount = window.__xmPages.length;
                                window.__xmLastImageAt = now;
                            }
                            if (!window.__xmStatusAt || now-window.__xmStatusAt > 1500) {
                                window.__xmStatusAt = now;
                                window.xmangapages.post(JSON.stringify({stage:'images ' + window.__xmPages.length +
                                    ', en attente ' + pending + ', ' + (atEnd ? 'bas' : 'défilement')}));
                            }
                            // A single image from the site's header or a broken lazy
                            // placeholder must not keep the reader blocked indefinitely.
                            // Require the bottom and a stable image count, and give
                            // remaining pages time to finish without endless waiting.
                            if (atEnd && window.__xmPages.length &&
                                now-(window.__xmFinishedAt || now)>4500 &&
                                now-(window.__xmLastImageAt || now)>3500 &&
                                (pending === 0 || now-(window.__xmFinishedAt || now)>14000)) {
                                window.xmangapages.post(JSON.stringify(window.__xmPages));
                                return 'done';
                            }
                            return 'waiting';
                        })()
                        """.trimIndent(),
                    )
                }
                loadUrl(chapterUrl)
            }
        } catch (error: WebViewTimeoutException) {
            throw IOException("X-Manga : lecteur non initialisé (" + lastState.get() + ").", error)
        }
        val array = JSONArray(raw)
        val images = (0 until array.length()).map(array::getString)
        if (images.isEmpty()) throw IOException("X-Manga : aucune image disponible dans ce chapitre.")
        return images.distinct().mapIndexed { idx, image -> Page(idx, url = chapterUrl, imageUrl = image) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder().apply {
        page.imageUrl?.let(imageHeaders::get)?.forEach { (key, value) ->
            if (!key.equals("Host", ignoreCase = true) && !key.equals("Content-Length", ignoreCase = true)) {
                header(key, value)
            }
        }
        header("Referer", page.url.takeIf { it.startsWith(baseUrl) } ?: "$baseUrl/")
    }.build()

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

package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import android.webkit.CookieManager
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
                    if (request.url.scheme == "https" &&
                        (request.url.host == "reader.hentai.gifts" ||
                            request.url.host.endsWith(".reader.hentai.gifts"))
                    ) {
                        imageHeaders[url] = request.requestHeaders
                    }
                    null
                }
                poll(650.milliseconds) {
                    evaluateJs(
                        """
                        (function() {
                            const body = document.body, root = document.scrollingElement;
                            if (!body || !root) return 'waiting';
                            if (/just a moment|access denied|attention required/i.test(document.title)) {
                                window.xmangapages.post(JSON.stringify({stage: 'vérification du site : ' + document.title}));
                                return 'challenge';
                            }
                            // Only the chapter's image CDN is trusted. X-Manga also
                            // renders decorative cover/avatar images in this same DOM.
                            const images = Array.from(body.querySelectorAll('img'));
                            const readerUrls = [];
                            let loaded = 0, pending = 0, blobImages = 0;
                            for (const img of images) {
                                img.loading = 'eager';
                                const sources = [img.getAttribute('data-src'), img.getAttribute('data-original'),
                                    img.getAttribute('data-lazy-src'), img.currentSrc, img.getAttribute('src')];
                                const srcset = img.getAttribute('data-srcset') || img.getAttribute('srcset');
                                if (srcset) sources.push(srcset.split(',').pop().trim().split(/\s+/)[0]);
                                let imageUrl = null;
                                for (const raw of sources) {
                                    if (!raw || raw.startsWith('data:')) continue;
                                    if (raw.startsWith('blob:')) { blobImages++; continue; }
                                    let url;
                                    try { url = new URL(raw, location.href); } catch (_) { continue; }
                                    if (!/^https?:$/.test(url.protocol)) continue;
                                    if (url.hostname === 'reader.hentai.gifts' ||
                                        url.hostname.endsWith('.reader.hentai.gifts')) {
                                        imageUrl = url.href;
                                        break;
                                    }
                                }
                                if (!imageUrl) continue;
                                readerUrls.push(imageUrl);
                                if (img.complete && img.naturalWidth > 80 && img.naturalHeight > 80) loaded++;
                                else pending++;
                            }
                            // The site initially shows "Chargement de la page..." for
                            // each slot. Keep that count until all slots are discovered,
                            // including when the reader virtualizes previous images.
                            const placeholders = Array.from(body.querySelectorAll('div,span,p'))
                                .filter(el => el.children.length === 0 &&
                                    /^chargement de la page/i.test((el.textContent || '').trim())).length;
                            window.__xmExpected = Math.max(window.__xmExpected || 0,
                                placeholders, readerUrls.length);
                            window.__xmSeen = window.__xmSeen || new Set();
                            for (const url of readerUrls) window.__xmSeen.add(url);
                            // Scroll the chapter itself. Scrolling every unrelated
                            // carousel can make the completion check run forever.
                            const scrollables = [root, ...Array.from(
                                body.querySelectorAll('main,article,[class*="reader"],[class*="chapter"]')
                            ).filter(el => {
                                if (el.scrollHeight <= el.clientHeight + 80) return false;
                                return /auto|scroll/.test(getComputedStyle(el).overflowY);
                            })].slice(0, 5);
                            const step = Math.max(480, Math.floor(innerHeight * 1.2));
                            let scrolling = false;
                            for (const el of scrollables) {
                                const max = Math.max(0, el.scrollHeight - el.clientHeight);
                                const next = Math.min(max, el.scrollTop + step);
                                if (next > el.scrollTop + 1) {
                                    el.scrollTop = next;
                                    scrolling = true;
                                }
                            }
                            const now = Date.now();
                            if (window.__xmSeenCount !== window.__xmSeen.size) {
                                window.__xmSeenCount = window.__xmSeen.size;
                                window.__xmChangedAt = now;
                            }
                            if (scrolling) window.__xmBottomAt = 0;
                            else if (!window.__xmBottomAt) window.__xmBottomAt = now;
                            if (!window.__xmStatusAt || now - window.__xmStatusAt > 1800) {
                                window.__xmStatusAt = now;
                                const status = 'pages CDN=' + window.__xmSeen.size +
                                    '/' + window.__xmExpected + ', chargées=' + loaded +
                                    ', en attente=' + pending + ', placeholders=' + placeholders +
                                    ', blob=' + blobImages + ', zones à défiler=' + scrollables.length;
                                window.xmangapages.post(JSON.stringify({stage: status}));
                            }
                            if (window.__xmSeen.size > 0 && window.__xmSeen.size >= window.__xmExpected &&
                                pending === 0 && placeholders === 0 && !scrolling &&
                                now - (window.__xmBottomAt || now) > 3000 &&
                                now - (window.__xmChangedAt || now) > 3000) {
                                // Prefer DOM order to discovery order: lazy loads sometimes
                                // complete out of order. Keep previously seen URLs if DOM
                                // virtualization has removed them.
                                const ordered = [...new Set(readerUrls)];
                                for (const url of window.__xmSeen) if (!ordered.includes(url)) ordered.push(url);
                                window.xmangapages.post(JSON.stringify(ordered));
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
            if (key.lowercase() !in setOf(
                    "host",
                    "content-length",
                    "accept-encoding",
                    "connection",
                    "cookie",
                    "sec-fetch-dest",
                    "sec-fetch-mode",
                    "sec-fetch-site",
                )
            ) {
                header(key, value)
            }
        }
        // A visible WebView may pass Cloudflare/CDN cookies even though its
        // shouldInterceptRequest headers omit Cookie. Mihon's separate OkHttp
        // image request must use the *target host's* browser cookies, without
        // leaking x-manga.org cookies to a third-party image host.
        page.imageUrl?.let { imageUrl ->
            runCatching { CookieManager.getInstance().getCookie(imageUrl) }
                .getOrNull()?.takeIf(String::isNotBlank)?.let { header("Cookie", it) }
        }
        header("User-Agent", headers["User-Agent"]!!)
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

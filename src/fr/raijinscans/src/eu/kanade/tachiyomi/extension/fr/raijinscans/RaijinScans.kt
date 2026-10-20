package eu.kanade.tachiyomi.extension.fr.raijinscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

@Source
abstract class RaijinScans : HttpSource() {
    override val supportsLatest = true

    override val client: OkHttpClient = super.client.newBuilder()
        .apply {
            val configured = interceptors()
            configured.indices.forEach { index ->
                val interceptor = configured[index]
                if (interceptor.javaClass.simpleName == "CloudflareInterceptor") {
                    configured[index] = Interceptor { chain ->
                        if (chain.request().url.host == baseUrl.toHttpUrl().host) {
                            chain.proceed(chain.request())
                        } else {
                            interceptor.intercept(chain)
                        }
                    }
                }
            }
        }
        .addInterceptor(
            AniverseTransport(baseUrl.toHttpUrl().host) { call, request ->
                val api = request.url.encodedPath.startsWith("/api/")
                val bridge = "aniverseReader"
                val payload = runWebViewBlocking<String>(call, timeout = 45.seconds) {
                    headers["User-Agent"]?.let { userAgent = it }
                    domStorageEnabled = true
                    blockImages = true
                    jsBridge(bridge) { message ->
                        val result = message.parseAs<BrowserResult>()
                        if (result.status in 200..299) {
                            resolve(result.body)
                        } else {
                            reject(IOException("Aniverse : HTTP ${result.status}. Validez l’accès dans la WebView puis réessayez."))
                        }
                    }
                    onReceivedError { resource, error ->
                        if (resource.isForMainFrame) reject(IOException(error.description.toString()))
                    }
                    onPageFinished {
                        val script = if (api) {
                            """
                        (async function(){
                            try {
                                const response = await fetch(${request.url.toString().toJsonString()}, {
                                    credentials: "same-origin",
                                    headers: { Accept: "application/json" }
                                });
                                window.$bridge.post(JSON.stringify({status: response.status, body: await response.text()}));
                            } catch (error) {
                                window.$bridge.post(JSON.stringify({status: 0, body: ""}));
                            }
                        })();
                            """.trimIndent()
                        } else {
                            """
                        (function(){
                            const ready = document.querySelector('script[src*="/_next/"], script#__NEXT_DATA__') !== null;
                            window.$bridge.post(JSON.stringify({status: ready ? 200 : 403, body: ready ? document.documentElement.outerHTML : ""}));
                        })();
                            """.trimIndent()
                        }
                        evaluateJs(script)
                    }
                    loadUrl(if (api) "$baseUrl/manga" else request.url.toString())
                }
                browserResponse(request, payload, api)
            },
        )
        .build()

    override fun headersBuilder() = super.headersBuilder().set("Referer", "$baseUrl/")

    // The site's full catalogue is paginated; the homepage only exposes a small selection.
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<CatalogResponse>()
        return MangasPage(
            data.items.map { item ->
                SManga.create().apply {
                    url = "/manga/${item.slug}"
                    title = item.title
                    thumbnail_url = item.image
                }
            },
            data.hasNextPage,
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) return latestUpdatesRequest(page)
        val url = "$baseUrl/api/anime/quicksearch".toHttpUrl().newBuilder()
            .addQueryParameter("media", "manga")
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath == "/api/manga") return latestUpdatesParse(response)
        val items = response.parseAs<List<CatalogManga>>()
        return MangasPage(
            items.map { item ->
                SManga.create().apply {
                    url = "/manga/${item.slug}"
                    title = item.title
                    thumbnail_url = item.image
                }
            },
            false,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<MangaDetailsPayload>()
            ?: error("Les données de la série Aniverse sont absentes.")
        return SManga.create().apply {
            url = "/manga/${data.manga.slug}"
            title = data.manga.title.userPreferred
            thumbnail_url = data.manga.coverImage.large
            description = data.manga.description?.let { Jsoup.parseBodyFragment(it).text() }
            author = data.manga.authors.joinToString()
            artist = data.manga.artists.joinToString()
            genre = data.manga.genres.joinToString()
            status = when (data.manga.status) {
                "FINISHED" -> SManga.COMPLETED
                "RELEASING" -> SManga.ONGOING
                "HIATUS" -> SManga.ON_HIATUS
                "CANCELLED" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<MangaDetailsPayload>()
            ?: error("La liste des chapitres Aniverse est absente.")
        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/read/${data.manga.slug}/${chapter.id}"
                name = "Chapitre ${chapter.number}" + chapter.title.orEmpty().takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(chapter.updatedAt)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (response.request.url.encodedPath == "/sign-in" || document.title() == "Connexion | Aniverse") {
            throw IOException("Aniverse réserve ce chapitre aux utilisateurs autorisés. Connectez-vous dans la WebView ou attendez sa disponibilité publique.")
        }
        val data = document.extractNextJs<ReaderPayload>()
            ?: error("Les pages Aniverse sont absentes. Vérifiez le chapitre dans la WebView.")
        require(data.pages.isNotEmpty()) { "Ce chapitre ne fournit aucune page accessible." }
        return data.pages.mapIndexed { index, page -> Page(index, imageUrl = page.src) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

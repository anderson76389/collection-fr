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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class RaijinScans : HttpSource() {
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("a[href^=/manga/]:has(img)")
            .distinctBy { it.attr("href") }
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("img")!!.attr("alt")
                    thumbnail_url = element.selectFirst("img")!!.absUrl("src")
                }
            }
        return MangasPage(mangas, false)
    }

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
                name = (if (dateFormat.tryParse(chapter.premiumUntil) > System.currentTimeMillis()) "🔒 " else "") + "Chapitre ${chapter.number}" + chapter.title.orEmpty().takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(chapter.updatedAt)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<ReaderPayload>()
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

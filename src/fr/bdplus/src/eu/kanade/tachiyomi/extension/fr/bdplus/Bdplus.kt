package eu.kanade.tachiyomi.extension.fr.bdplus

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
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.Normalizer

@Source
abstract class Bdplus : KeiSource() {
    override fun Headers.Builder.configureHeaders() = set("Referer", "$baseUrl/")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    private suspend fun catalogue(): Catalogue {
        val document = client.get("$baseUrl/catalogue.html").asJsoup()
        val url = document.selectFirst("#cgrid[data-src]")?.absUrl("data-src")
            ?: throw IOException("Le catalogue BDPlus a changé de format.")
        return client.get(url).parseAs()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = catalogue().rows
        .sortedByDescending { it.getOrNull(8)?.string?.toDoubleOrNull() ?: 0.0 }
        .toPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page != 1) return MangasPage(emptyList(), false)
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("#upd .ur").mapNotNull { card ->
            val link = card.selectFirst(".ti a[href*=serie_static]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val search = query.foldAccents()
        return catalogue().rows.filter { row ->
            search.isBlank() || listOf(0, 1, 5).any { row.getOrNull(it)?.string?.foldAccents()?.contains(search) == true }
        }.toPage(page)
    }

    private fun List<List<JsonPrimitive>>.toPage(page: Int): MangasPage {
        val offset = (page - 1) * 30
        val mangas = drop(offset).take(30).map { row ->
            val slug = row[0].string
            val cover = row[2].string
            SManga.create().apply {
                url = "/serie_static/$slug.html"
                title = row[1].string
                thumbnail_url = when {
                    cover.isBlank() -> "$baseUrl/assets/images/cover-fallback.webp"
                    cover.startsWith("/") -> "$baseUrl$cover"
                    else -> "$baseUrl/_converted/$slug/$cover/cover.webp"
                }
            }
        }
        return MangasPage(mangas, offset + mangas.size < size)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/serie_static/")) return null
        return details(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    private fun details(document: Document) = SManga.create().apply {
        title = document.selectFirst("article.serie h1")?.text().orEmpty()
        thumbnail_url = document.selectFirst(".fh-cov img")?.absUrl("src")
        author = document.select(".fh-au .an b").eachText().distinct().joinToString()
        genre = document.select(".fh-meta .pg").map { it.ownText() }.joinToString()
        description = document.select(".syn .lead, #synMore p").eachText().joinToString("\n\n")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updated = if (fetchDetails) details(document).apply { url = manga.url } else manga
        val tomes = if (fetchChapters) {
            document.select("#tl a.tr[data-k]").map { row ->
                SChapter.create().apply {
                    setUrlWithoutDomain(row.absUrl("href"))
                    name = listOf(row.select(".tn").text(), row.select(".tt").text()).filter { it.isNotBlank() }.joinToString(" · ")
                    memo = buildJsonObject { put("directory", "/_converted/${row.attr("data-k")}/") }
                }
            }.asReversed()
        } else {
            chapters
        }
        return SMangaUpdate(updated, tomes)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val directory = chapter.memo.getString("directory")
        val readerHeaders = headers.newBuilder().set("Referer", getChapterUrl(chapter)).build()
        val dto = client.get("$baseUrl${directory}pages.json", readerHeaders).parseAs<ReaderPages>()
        val imageBase = "$baseUrl$directory".toHttpUrl()
        return dto.pages.mapIndexed { index, image ->
            Page(index, imageUrl = imageBase.resolve(image.url)?.toString())
        }
    }

    private fun String.foldAccents() = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase()
}

@Serializable
class Catalogue(val rows: List<List<JsonPrimitive>>)

@Serializable
class ReaderPages(val pages: List<ReaderImage>)

@Serializable
class ReaderImage(val url: String)

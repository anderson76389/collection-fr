package eu.kanade.tachiyomi.extension.all.flixnetcoffeemanga

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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class FlixnetCoffeemanga : KeiSource() {
    override fun Headers.Builder.configureHeaders() = set("Referer", "$baseUrl/")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int) = browse(page, "views")

    override suspend fun getLatestUpdates(page: Int) = browse(page, "latest")

    private suspend fun browse(page: Int, order: String): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", order)
            .addQueryParameter("page", page.toString())
            .build()
        return listing(client.get(url).asJsoup(), page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
            .addQueryParameter("page", page.toString())
            .build()
        return listing(client.get(url).asJsoup(), page)
    }

    private fun listing(document: Document, page: Int): MangasPage {
        val mangas = document.select(".page-item-detail").mapNotNull { card ->
            val link = card.selectFirst(".widget-title a, .post-title a, .c-image-hover a") ?: return@mapNotNull null
            val image = card.selectFirst("img")
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text().ifBlank { link.attr("title") }.removeSuffix(" on CoffeeManga")
                thumbnail_url = image?.absUrl("data-src")?.takeIf { it.isNotBlank() } ?: image?.absUrl("src")
            }
        }.distinctBy { it.url }
        val nextPage = document.select("a[href]").any { link ->
            link.absUrl("href").toHttpUrlOrNull()?.queryParameter("page") == (page + 1).toString()
        }
        return MangasPage(mangas, nextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in setOf(baseUrl.toHttpUrl().host, "www.zazamanga.com")) return null
        if (url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 2) return null
        val manga = SManga.create().apply { this.url = "/manga/${url.pathSegments[1]}" }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updated = if (fetchDetails) {
            SManga.create().apply {
                url = manga.url
                title = document.selectFirst(".post-title h1, h1")?.text().orEmpty()
                thumbnail_url = document.selectFirst(".summary_image img")?.absUrl("src")
                description = document.select(".summary__content, .description-summary .summary__content").text()
                author = document.select(".author-content a").eachText().distinct().joinToString()
                artist = document.select(".artist-content a").eachText().distinct().joinToString()
                genre = document.select(".genres-content a").eachText().distinct().joinToString()
                val state = document.select(".post-status .summary-content").text().lowercase()
                status = when {
                    "ongoing" in state -> SManga.ONGOING
                    "completed" in state -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        } else {
            manga
        }
        val chapterList = if (fetchChapters) {
            document.select(".chapter-list a[href*=/chapter-]").map { link ->
                SChapter.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    name = link.text()
                }
            }.distinctBy { it.url }
        } else {
            chapters
        }
        return SMangaUpdate(updated, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".reading-content img.wp-manga-chapter-img").mapIndexed { index, image ->
            val imageUrl = image.absUrl("data-src").ifBlank { image.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }
    }
}

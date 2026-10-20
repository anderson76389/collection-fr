package eu.kanade.tachiyomi.extension.fr.lelmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Lelmanga : MangaThemesia() {
    override val altNamePrefix = "Nom alternatif: "
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i"
    override val seriesArtistSelector = ".imptdt:contains(Artiste) i"
    override val sendViewCount = false

    private val documents = RecentDocuments()

    private suspend fun document(url: String): Document = documents.get(url) { client.get(url).asJsoup() }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val doc = document(getMangaUrl(manga))
        return SMangaUpdate(
            if (fetchDetails) mangaDetailsParse(doc) else manga,
            if (fetchChapters) chapterListParse(doc) else chapters,
        )
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = searchMangaParse(document(getMangaUrl(manga)))
        .mangas.filterNot { it.title.isEmpty() }

    override suspend fun getPageList(chapter: SChapter) = pageListParse(document(getChapterUrl(chapter)))

    override fun searchMangaFromElement(element: Element) = super.searchMangaFromElement(element).apply {
        thumbnail_url = thumbnail_url?.directImageUrl()
    }

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url?.directImageUrl()
    }

    override fun imageRequest(page: Page) = GET(
        page.imageUrl!!.directImageUrl(),
        headers.newBuilder().set("Referer", baseUrl).build(),
    )

    private fun String.directImageUrl(): String {
        val url = toHttpUrlOrNull() ?: return this
        val base = baseUrl.toHttpUrl()
        if (!WORDPRESS_IMAGE_HOST.matches(url.host)) return this
        val prefix = "/${base.host}/wp-content/uploads/"
        if (!url.encodedPath.startsWith(prefix)) return this
        return base.newBuilder()
            .encodedPath(url.encodedPath.removePrefix("/${base.host}"))
            .build().toString()
    }
}

private val WORDPRESS_IMAGE_HOST = Regex("i[0-3]\\.wp\\.com")

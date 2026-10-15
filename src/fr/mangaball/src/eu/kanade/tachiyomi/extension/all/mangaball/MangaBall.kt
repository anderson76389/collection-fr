package eu.kanade.tachiyomi.extension.all.mangaball

import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException

@Source
abstract class MangaBall :
    KeiSource(),
    ConfigurableSource {

    private val siteLang = listOf("fr")

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = searchAdvanced(page, "", FilterList(), sortBy = "views", sortOrder = "desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchAdvanced(page, "", FilterList(), sortBy = "lastupdate", sortOrder = "desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchAdvanced(page, query, filters)

    private suspend fun searchAdvanced(
        page: Int,
        query: String,
        filters: FilterList,
        sortBy: String? = null,
        sortOrder: String? = null,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()

        // The API only orders by relevance when sort_by is omitted, which "Relevance" maps to.
        // Keyword-less browse falls back to last-updated so it stays ordered.
        val effectiveSortBy = sortBy ?: sort?.sortBy ?: if (query.isBlank()) "lastupdate" else null

        val url = "$baseUrl/api/v1/title/search-advanced".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (effectiveSortBy != null) {
                addQueryParameter("sort_by", effectiveSortBy)
                addQueryParameter("sort_order", sortOrder ?: sort?.sortOrder ?: "desc")
            }
            addQueryParameter("tag_mode", filters.firstInstanceOrNull<TagModeFilter>()?.selected ?: "AND")
            addQueryParameter("adult_mode", "no_18")
            addQueryParameter("translated_languages", "fr")

            if (query.isNotBlank()) {
                addQueryParameter("keyword", query.trim())
            }

            filters.firstInstanceOrNull<TypeFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("publicationDemographic", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("status", it) }

            val included = filters.filterIsInstance<TriStateGroupFilter<String>>().flatMap { it.included }
            if (included.isNotEmpty()) {
                addQueryParameter("included_tags", included.joinToString(","))
            }

            val excluded = (filters.filterIsInstance<TriStateGroupFilter<String>>().flatMap { it.excluded } + ADULT_TAG_IDS).distinct()
            if (excluded.isNotEmpty()) {
                addQueryParameter("excluded_tags", excluded.joinToString(","))
            }
        }.build()

        return client.get(url, headers).parseAs<SearchResponse>().toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        DemographicFilter(),
        StatusFilter(),
        TagModeFilter(),
        ContentFilter(),
        FormatFilter(),
        GenreFilter(),
        ThemeFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != LEGACY_HOST) return null

        val id = when (url.pathSegments.firstOrNull()) {
            "title-detail" -> url.pathSegments.getOrNull(1)
            "chapter-detail" -> url.pathSegments.getOrNull(1)
                ?.let { chapterId ->
                    client.get("$baseUrl/api/v1/chapter-detail?chapter_id=$chapterId")
                        .parseAs<ChapterDetailResponse>()
                        .data.chapter.titleId
                }
            else -> null
        } ?: return null

        return getMangaDetails(id)
    }

    override fun getMangaUrl(manga: SManga): String {
        // The slug form only server-renders a stub page, so link to the id form the site itself uses.
        val id = manga.memo.getStringOrNull("id") ?: manga.url
        return "$baseUrl/title-detail/$id"
    }

    private suspend fun getMangaDetails(idOrSlug: String): SManga {
        // Older library URLs ended with the title ID; the new API must receive that ID.
        val legacyId = idOrSlug.substringAfterLast("-")
        val id = if (TITLE_ID.matches(legacyId)) {
            legacyId
        } else {
            // The detail endpoint accepts a slug but can return an unrelated title (one-piece -> one).
            val searchUrl = "$baseUrl/api/v1/title/search-advanced".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", idOrSlug.replace('-', ' '))
                .addQueryParameter("adult_mode", "no_18")
                .addQueryParameter("translated_languages", "fr")
                .addQueryParameter("excluded_tags", ADULT_TAG_IDS.joinToString(","))
                .addQueryParameter("limit", "100")
                .build()
            client.get(searchUrl).parseAs<SearchResponse>().data
                .singleOrNull { it.slug == idOrSlug }?.id
                ?: throw IOException("Titre introuvable par son ancienne adresse. Recherchez cette œuvre à nouveau dans la source.")
        }
        return client.get("$baseUrl/api/v1/title/detail/$id")
            .parseAs<TitleResponse>()
            .data
            .also { it.requireFrenchNonAdult() }
            .toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val titleId = manga.url.takeIf(TITLE_ID::matches) ?: manga.memo.getStringOrNull("id")

        return if (fetchChapters && titleId != null) {
            coroutineScope {
                val mangaDeferred = async { getMangaDetails(titleId) }
                val chaptersDeferred = async { getChapterList(titleId) }

                SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
            }
        } else {
            // Entries saved by older versions have no id in memo, so one details fetch is needed to
            // resolve it. Returning that manga persists the id, saving the fetch on later refreshes.
            val updatedManga = if (titleId == null || fetchDetails) getMangaDetails(titleId ?: manga.url) else manga

            val updatedChapters = if (fetchChapters) {
                getChapterList(updatedManga.memo.getString("id"))
            } else {
                chapters
            }

            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    private suspend fun getChapterList(titleId: String): List<SChapter> {
        val body = TitleIdRequest(titleId).toJsonBody()

        val response = client.post("$baseUrl/api/v1/chapter/chapter-listing-by-title-id", headers, body)
            .parseAs<ChapterListResponse>()
        if (response.data.isEmpty()) {
            throw IOException("MangaBall : le serveur renvoie une liste vide pour cette œuvre (ID : $titleId).")
        }

        val chapters = response.data.mapNotNull { it.toSChapter(siteLang) }
        if (chapters.isEmpty()) {
            throw IOException("MangaBall : des chapitres existent, mais aucun ne correspond au français. Cette source propose uniquement les chapitres français.")
        }
        preferences.rememberScanlators(chapters.mapNotNull { it.scanlator })

        return chapters.filterBlacklistedScanlators(preferences.scanlatorBlacklist()).also {
            if (it.isEmpty()) {
                throw IOException("MangaBall : tous les chapitres sont masqués par la liste noire des équipes. Vérifiez les réglages de l’extension.")
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter-detail/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("$baseUrl/api/v1/chapter-detail?chapter_id=${chapter.url}")
            .parseAs<ChapterDetailResponse>()
            .data.chapter.pages

        if (pages.isEmpty()) {
            throw IOException("MangaBall : le serveur ne renvoie aucune page pour ce chapitre (ID : ${chapter.url}).")
        }
        updateViews(chapter.url)

        return pages.mapIndexed { index, url -> Page(index, getChapterUrl(chapter), url) }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers.newBuilder().set("Referer", page.url).build())

    private fun updateViews(chapterId: String) {
        val body = ViewRequest(chapterId, "chapter").toJsonBody()
        val request = POST("$baseUrl/api/v1/views/update", headers, body)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to update views", e)
                    }
                },
            )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addScanlatorBlacklistPreference(preferences)
    }
}

private const val LEGACY_HOST = "mangaball.net"
private val TITLE_ID = Regex("[a-f0-9]{24}")

// OkHttp 5 appends "; charset=utf-8" when a request body is built from a String, and this API
// rejects any Content-Type other than a bare "application/json" with HTTP 400.
private inline fun <reified T> T.toJsonBody(): RequestBody = toJsonString().toByteArray().toRequestBody(JSON_MEDIA_TYPE)

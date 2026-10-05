package eu.kanade.tachiyomi.extension.fr.raijinscans

import kotlinx.serialization.Serializable

@Serializable
class CatalogResponse(val items: List<CatalogManga>, val hasNextPage: Boolean)

@Serializable
class CatalogManga(val slug: String, val title: String, val image: String?)

@Serializable
class MangaDetailsPayload(val manga: MangaDetails, val chapters: List<ChapterData>)

@Serializable
class MangaDetails(
    val slug: String,
    val title: MangaTitle,
    val coverImage: CoverImage,
    val description: String?,
    val status: String,
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
class MangaTitle(val userPreferred: String)

@Serializable
class CoverImage(val large: String)

@Serializable
class ChapterData(val id: String, val number: String, val title: String?, val updatedAt: String)

@Serializable
class ReaderPayload(val mangaId: String, val publicId: String, val pages: List<ReaderPage>)

@Serializable
class ReaderPage(val src: String)

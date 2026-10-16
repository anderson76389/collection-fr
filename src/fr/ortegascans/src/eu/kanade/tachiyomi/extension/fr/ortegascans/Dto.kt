package eu.kanade.tachiyomi.extension.fr.ortegascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class SeriesResponse(
    val data: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class SeriesDto(
    val id: String,
    val title: String,
    val slug: String,
    val coverImage: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        memo = buildJsonObject { put("id", id) }
        title = this@SeriesDto.title
        thumbnail_url = coverUrl(baseUrl, coverImage)
    }
}

@Serializable
class MangaDetailsDataDto(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String? = null,
    val coverImage: String,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val alternativeNames: String? = null,
    val categories: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        memo = buildJsonObject { put("id", id) }
        title = this@MangaDto.title
        thumbnail_url = coverUrl(baseUrl, coverImage)
        description = listOfNotNull(
            this@MangaDto.description,
            this@MangaDto.alternativeNames?.let { "Noms alternatifs : $it" },
        ).joinToString("\n\n")
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        status = parseStatus(this@MangaDto.status)
        genre = categories.joinToString()
    }
}

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    val title: String? = null,
    val isPremium: Boolean = false,
    val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterNumber = number.toString().removeSuffix(".0")

        url = id
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
            put("number", chapterNumber)
        }
        name = buildString {
            if (isPremium) append("🔒 ")
            append("Chapitre ")
            append(chapterNumber)
            if (title != null) {
                append(" - ")
                append(title)
            }
        }
        chapter_number = number
        date_upload = Instant.tryParse(createdAt.removePrefix($$"$D"))
    }
}

@Serializable
class PageListDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val index: Int,
    val url: String,
)

private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
    "en cours", "ongoing" -> SManga.ONGOING
    "terminé", "complete" -> SManga.COMPLETED
    "en pause", "on hold" -> SManga.ON_HIATUS
    "annulé", "canceled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

internal fun coverUrl(baseUrl: String, coverImage: String): String? {
    val base = baseUrl.toHttpUrl()
    val url = base.resolve(coverImage) ?: return null
    if (url.host != base.host) return url.toString()

    val path = url.encodedPath.replaceFirst("/storage/", "/api/")
    val builder = url.newBuilder().encodedPath(path)
    if (path.startsWith("/api/covers/")) {
        // The current reader moves the stored filename hash into a cache-version query.
        COVER_FILENAME.matchEntire(url.pathSegments.last())?.destructured?.let { (name, hash, extension) ->
            builder.encodedPath("/api/covers/$name.$extension")
                .setQueryParameter("v", hash)
        }
    }
    return builder.build().toString()
}

private val COVER_FILENAME = Regex("(.+)_([a-fA-F0-9]{8})\\.([a-zA-Z0-9]+)")

package eu.kanade.tachiyomi.extension.all.mangaball

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class SearchResponse(
    val data: List<MangaDto>,
    private val pagination: Pagination,
) {
    @Serializable
    class Pagination(
        private val page: Int,
        @SerialName("total_pages")
        private val totalPages: Int,
    ) {
        fun hasNextPage() = page < totalPages
    }

    fun toMangasPage() = MangasPage(data.filter { it.isFrenchNonAdult() }.map { it.toSManga() }, pagination.hasNextPage())
}

@Serializable
class MangaDto(
    val id: String,
    val slug: String,
    private val name: String,
    private val image: ImageDto? = null,
    private val availableTranslatedLanguages: List<String> = emptyList(),
    private val is18plus: Boolean = false,
    private val tags: List<TagDto> = emptyList(),
) {
    fun isFrenchNonAdult() = "fr" in availableTranslatedLanguages && !is18plus && tags.none { it.isAdult() }

    fun toSManga() = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = image?.url
        memo = buildJsonObject { put("id", id) }
    }
}

@Serializable
class TitleResponse(
    val data: TitleDto,
)

@Serializable
class TitleDto(
    private val id: String,
    private val slug: String,
    private val name: String,
    private val image: ImageDto? = null,
    private val description: List<String> = emptyList(),
    private val alternateName: List<String> = emptyList(),
    private val tags: List<TagDto> = emptyList(),
    private val author: List<AuthorDto> = emptyList(),
    private val status: String? = null,
    private val availableTranslatedLanguages: List<String> = emptyList(),
    private val is18plus: Boolean = false,
) {
    fun requireFrenchNonAdult() {
        if (is18plus || tags.any { it.isAdult() }) {
            throw java.io.IOException("MangaBall : le contenu adulte est exclu de cette source.")
        }
        if ("fr" !in availableTranslatedLanguages) {
            throw java.io.IOException("MangaBall : cette œuvre ne propose pas de traduction française.")
        }
    }

    fun toSManga(): SManga {
        val altNames = alternateName.joinToString("\n") { "- $it" }
        val description = buildString {
            append(this@TitleDto.description.joinToString("\n\n"))
            if (altNames.isNotBlank()) {
                append("\n\nAlternative Names: \n", altNames)
            }
        }.trim()

        return SManga.create().apply {
            url = id
            title = name
            thumbnail_url = image?.url
            genre = tags.joinToString { it.name }
            author = this@TitleDto.author.joinToString { it.name }
            this.description = description
            memo = buildJsonObject { put("id", id) }
            this.status = when (this@TitleDto.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ImageDto(
    private val file: String? = null,
    @SerialName("cdn_mangadex")
    private val cdnMangadex: String? = null,
    @SerialName("cdn_mangaupdate")
    private val cdnMangaupdate: String? = null,
    @SerialName("cdn_mangaupdates")
    private val cdnMangaupdates: String? = null,
    private val cover: CoverDto? = null,
) {
    // Mirrors the site's getTitleImage priority: the self-hosted cover first, external mirrors only as fallbacks.
    val url: String?
        get() = cover?.url
            ?: listOf(file, cdnMangadex, cdnMangaupdate, cdnMangaupdates)
                .firstOrNull { !it.isNullOrBlank() }
}

@Serializable
class CoverDto(
    private val path: String? = null,
) {
    // `path` is a Windows-style relative path such as "<titleId>\\cover_123.jpg".
    val url: String?
        get() = path?.trim()
            ?.replace('\\', '/')
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http")) it else COVER_BASE_URL + it }
}

@Serializable
class TagDto(
    val name: String,
) {
    fun isAdult() = name.lowercase() in ADULT_TAGS
}

private val ADULT_TAGS = setOf("hentai", "pornographic", "erotica", "smut", "manhwa 18+", "adult", "18+", "mature", "loli", "shota")

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val name: String? = null,
    private val number: Float? = null,
    private val volume: Float = 0f,
    private val lang: String,
    private val group: GroupDto? = null,
    @SerialName("created_at")
    private val createdAt: String? = null,
) {
    fun toSChapter(langs: List<String>): SChapter? {
        if (langs.isNotEmpty() && lang !in langs) return null

        val chapterName = name.orEmpty().trim()

        return SChapter.create().apply {
            url = id
            name = buildString {
                if (langs.isEmpty()) append("[$lang] ")
                if (volume > 0) {
                    append("Vol. ", volume.toString().removeSuffix(".0"), " ")
                }
                val numberStr = number.toString().removeSuffix(".0")
                if (chapterName.contains(numberStr)) {
                    append(chapterName)
                } else {
                    append("Ch. ", numberStr)
                    if (chapterName.isNotEmpty()) {
                        append(" ", chapterName)
                    }
                }
            }
            if (number != null) {
                chapter_number = number
            }
            date_upload = dateFormat.tryParseDateTime(createdAt, ZoneOffset.UTC)
            scanlator = group?.name
        }
    }
}

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

private const val COVER_BASE_URL = "https://bulbasaur.poke-black-and-white.net/covers/"

@Serializable
class GroupDto(
    val name: String,
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterDetailData,
) {
    @Serializable
    class ChapterDetailData(
        val chapter: ChapterDetailDto,
    )
}

@Serializable
class ChapterDetailDto(
    @SerialName("title_id")
    val titleId: String? = null,
    val pages: List<String> = emptyList(),
)

@Serializable
class TitleIdRequest(
    @SerialName("title_id")
    private val titleId: String,
)

@Serializable
class ViewRequest(
    @SerialName("object_id")
    private val objectId: String,
    @SerialName("object_type")
    private val objectType: String,
)

internal val ADULT_TAG_IDS = setOf(
    "689371f0a943baf927094f03",
    "68932d11a943baf927094e7b",
    "689371f2a943baf927094f04",
    "68bfceaf4dbc442a26519889",
    "685148d715e8b86aae68e506",
    "68f5f5ce5f29d3c1863dec3a",
    "685148d115e8b86aae68e4ed",
)

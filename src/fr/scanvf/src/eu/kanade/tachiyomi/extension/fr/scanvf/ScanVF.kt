package eu.kanade.tachiyomi.extension.fr.scanvf

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import kotlin.math.min

@Source
abstract class ScanVF : MMRCMS() {

    override val itemPath = ""

    override val supportsAdvancedSearch = false

    override fun parseSearchDirectory(page: Int): MangasPage {
        val start = min((page - 1) * 24, searchDirectory.size)
        val end = min(page * 24, searchDirectory.size)
        val manga = searchDirectory.subList(start, end)
            .map {
                SManga.create().apply {
                    url = "/${it.data}"
                    title = it.value
                    thumbnail_url = guessCover(url, null)
                }
            }
        val hasNextPage = end < searchDirectory.size

        return MangasPage(manga, hasNextPage)
    }
}

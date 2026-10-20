package eu.kanade.tachiyomi.extension.fr.bananascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient

@Source
abstract class HarmonyScan : Madara() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(OriginalCoverInterceptor())

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = originalCoverUrl(url)

    override val chapterMode = ChapterMode.MangaAjax
}

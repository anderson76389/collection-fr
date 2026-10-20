package eu.kanade.tachiyomi.extension.fr.pantheonscan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class PantheonScan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(OriginalCoverInterceptor())

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = originalCoverUrl(url)

    override val chapterMode = ChapterMode.MangaAjax
}

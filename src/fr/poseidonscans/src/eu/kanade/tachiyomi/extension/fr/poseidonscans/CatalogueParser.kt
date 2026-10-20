package eu.kanade.tachiyomi.extension.fr.poseidonscans

import org.jsoup.nodes.Document
import java.net.URLDecoder

internal class CatalogueEntry(val title: String, val url: String, val cover: String?)
internal class CatalogueResult(val entries: List<CatalogueEntry>, val hasNextPage: Boolean)

internal fun parseCatalogue(document: Document): CatalogueResult {
    val entries = document.select("a.block.group[href^=/serie/]").mapNotNull { card ->
        val title = card.selectFirst("h2")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val url = card.absUrl("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val image = card.selectFirst("img[alt]:not([alt=''])") ?: card.selectFirst("img")
        val optimized = image?.attr("srcset")?.substringBefore(' ')?.takeIf(String::isNotBlank)
            ?: image?.attr("src")?.takeIf(String::isNotBlank)
        val cover = optimized?.let {
            if (it.contains("url=")) URLDecoder.decode(it.substringAfter("url=").substringBefore('&'), "UTF-8") else it
        }
        CatalogueEntry(title, url, cover)
    }.distinctBy { it.url }
    val hasNext = document.select("nav[aria-label=Pagination] a").any { it.text().contains("Suivant", ignoreCase = true) }
    return CatalogueResult(entries, hasNext)
}

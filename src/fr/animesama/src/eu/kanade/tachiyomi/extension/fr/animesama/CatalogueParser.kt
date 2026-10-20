package eu.kanade.tachiyomi.extension.fr.animesama

import org.jsoup.nodes.Document

internal class CatalogueEntry(val title: String, val url: String, val cover: String?)
internal class CatalogueResult(val entries: List<CatalogueEntry>, val hasNextPage: Boolean)

internal fun parseCatalogue(document: Document, containerSelector: String, suffix: String = ""): CatalogueResult {
    val entries = document.select(containerSelector).mapNotNull { card ->
        val title = card.selectFirst(".as-carte-titre, h2.card-title")?.text()?.takeIf(String::isNotBlank)
            ?: card.attr("data-nom").takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val link = card.selectFirst("a.as-carte-lien[href], a[href]")?.absUrl("href")?.takeIf(String::isNotBlank)
            ?: card.absUrl("data-url").takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val url = if (suffix.isEmpty()) link else link.trimEnd('/').removeSuffix(suffix.trimEnd('/'))
        val image = card.selectFirst(".as-carte-image img, img")
        val cover = image?.absUrl("data-src")?.takeIf(String::isNotBlank)
            ?: image?.absUrl("src")?.takeIf(String::isNotBlank)
        CatalogueEntry(title, url, cover)
    }.distinctBy { it.url }
    val hasNext = document.selectFirst("#list_pagination .pagi-actuelle + a, #list_pagination > a.bg-sky-900 + a") != null
    return CatalogueResult(entries, hasNext)
}

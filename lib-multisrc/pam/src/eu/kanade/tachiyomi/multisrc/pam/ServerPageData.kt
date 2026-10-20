package eu.kanade.tachiyomi.multisrc.pam

import org.jsoup.nodes.Document

internal fun serverPageData(document: Document): String? = document.selectFirst("#app[data-page]")?.attr("data-page")?.takeIf(String::isNotBlank)
    ?: document.selectFirst("script[data-page=app][type=application/json]")?.data()?.takeIf(String::isNotBlank)

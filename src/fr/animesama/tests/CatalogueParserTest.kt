package eu.kanade.tachiyomi.extension.fr.animesama

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueParserTest {
    @Test
    fun redesignedCardsAndPaginationAreParsed() {
        val document = Jsoup.parse(
            """
            <div id="list_catalog">
                <div class="as-carte" data-nom="Ghost" data-img="https://img.example/original.jpg"><a class="as-carte-lien" href="/catalogue/ghost/">
                    <div class="as-carte-image"><img src="https://img.example/ghost.webp"></div>
                    <h3 class="as-carte-titre">Ghost</h3></a></div>
                <div class="loading-placeholder"></div>
                <div data-nom="Second" data-url="/catalogue/second/"></div>
            </div>
            <div id="list_pagination"><span class="pagi-actuelle">1</span><a href="?page=2">2</a></div>
            """.trimIndent(),
            "https://anime-sama.to/catalogue/",
        )
        val result = parseCatalogue(document, "#list_catalog > div")
        assertEquals(listOf("Ghost", "Second"), result.entries.map { it.title })
        assertEquals("https://anime-sama.to/catalogue/ghost/", result.entries.first().url)
        assertEquals("https://img.example/original.jpg#makoff-cover-v2", result.entries.first().cover)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun latestScanLinksReturnToTheWorkPage() {
        val document = Jsoup.parse(
            """
            <div id="recent"><div><a href="/catalogue/ghost/scan/vf"><h3 class="as-carte-titre">Ghost</h3></a></div></div>
            """.trimIndent(),
            "https://anime-sama.to/",
        )
        assertEquals("https://anime-sama.to/catalogue/ghost/", parseCatalogue(document, "#recent > div", "scan/vf/").entries.single().url)
    }

    @Test
    fun olderCardsRemainReadableAndTheLastPageDoesNotRepeat() {
        val document = Jsoup.parse("<div id='list_catalog'><div><a href='/catalogue/old/'><h2 class='card-title'>Old</h2></a></div></div>", "https://anime-sama.to/")
        val result = parseCatalogue(document, "#list_catalog > div")
        assertEquals("Old", result.entries.single().title)
        assertFalse(result.hasNextPage)
    }
}

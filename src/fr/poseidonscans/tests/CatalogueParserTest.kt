package eu.kanade.tachiyomi.extension.fr.poseidonscans

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueParserTest {
    @Test
    fun fullCatalogueCardsUseTheRealCoverAndFollowTheNextPage() {
        val document = Jsoup.parse(
            """
            <div class="grid"><a class="block group" href="/serie/first">
                <img alt="" src="/decorative.png"><h2>First</h2>
                <img alt="First" srcset="/_next/image?url=%2Fapi%2Fcovers%2Ffirst.webp&amp;w=640&amp;q=75 640w, /other 1080w">
            </a><a class="block group" href="/serie/second"><h2>Second</h2></a></div>
            <nav aria-label="Pagination"><a href="?sortBy=popular&amp;page=2">Suivant</a></nav>
            """.trimIndent(),
            "https://poseidon-scans.net/series?sortBy=popular&page=1",
        )
        val result = parseCatalogue(document)
        assertEquals(listOf("First", "Second"), result.entries.map { it.title })
        assertEquals("/api/covers/first.webp", result.entries.first().cover)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun duplicateCardsAndNonMangaLinksDoNotPolluteTheCatalogue() {
        val document = Jsoup.parse(
            """
            <a class="block group" href="/serie/one"><h2>One</h2></a>
            <a class="block group" href="/serie/one"><h2>One</h2></a>
            <a class="block group" href="/serie/loading"></a>
            <a class="block group" href="/contact"><h2>Contact</h2></a>
            """.trimIndent(),
            "https://poseidon-scans.net/",
        )
        val result = parseCatalogue(document)
        assertEquals(1, result.entries.size)
        assertFalse(result.hasNextPage)
    }
}

package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XMangaRoutesTest {
    @Test
    fun currentHomepageListsCanonicalMangaAndNotWordpressArchive() {
        val document = Jsoup.parse(
            """
            <main>
              <a href="/manga/un-xy-dans-un-monde-de-xx/">
                <img src="/cover.jpg" alt="Couverture de Un XY dans un monde de XX"/>
              </a>
              <a href="/read/b49de6d7-40f2-4d85-8547-0fec72a95f20/">Chapitre 83</a>
              <a href="/manga/un-xy-dans-un-monde-de-xx/">Un XY dans un monde de XX</a>
            </main>
            """.trimIndent(),
            "https://x-manga.org/",
        )
        val works = xMangaCards(document)
        assertEquals(1, works.size)
        assertEquals("/manga/un-xy-dans-un-monde-de-xx/", works.single().url)
        assertEquals("Un XY dans un monde de XX", works.single().title)
    }

    @Test
    fun readerUsesTheActualChapterImageHostAndHasBoundedLoading() {
        val script = XMANGA_CHAPTER_CAPTURE_SCRIPT
        assertTrue(script.contains("reader.hentai.gifts"))
        assertTrue(script.contains("window.__xmReaderSeen"))
        assertTrue(script.contains("performance.getEntriesByType('resource')"))
        assertTrue(script.contains("window.__xmStartedAt > 24000"))
        assertTrue(script.contains("window.xmangapages.post(JSON.stringify(pages))"))
    }

    @Test
    fun currentChapterUuidLinksArePreservedForWebview() {
        val document = Jsoup.parse(
            """
            <main><h1>Un XY dans un monde de XX</h1>
            <div>84 chapitres</div>
            <a href="/read/b49de6d7-40f2-4d85-8547-0fec72a95f20/">Chapitre 83 24 oct. 2026</a>
            <a href="/read/b49de6d7-40f2-4d85-8547-0fec72a95f20/">Chapitre 83</a>
            </main>
            """.trimIndent(),
            "https://x-manga.org/manga/un-xy-dans-un-monde-de-xx/",
        )
        assertEquals(84, xMangaChapterCount(document))
        val chapters = xMangaChapters(document)
        assertEquals(1, chapters.size)
        assertEquals("/read/b49de6d7-40f2-4d85-8547-0fec72a95f20/", chapters.single().url)
        assertEquals(83f, chapters.single().chapter_number)
        assertTrue(xMangaDetails(document, "/manga/un-xy-dans-un-monde-de-xx/").title.contains("Un XY"))
    }
}

package eu.kanade.tachiyomi.extension.fr.japscan

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadyTest {
    @Test
    fun mangaChapterSelectorSurvivesDifferentCollapseWrappers() {
        val document = Jsoup.parse("""
            <div id="list_chapters">
                <div class="collapse"><div class="list_chapters"><a href="/manga/one-piece/1/">Chapitre 1</a></div></div>
                <section><div class="list_chapters"><a href="/manga/one-piece/2/">Chapitre 2</a></div></section>
            </div>
        """.trimIndent())
        assertEquals(2, document.select(japscanChapterListSelector(hideSpoilers = true)).size)
    }

    @Test
    fun spoilerPreferenceIsRetainedInTheNewChapterSelector() {
        val document = Jsoup.parse("""
            <div id="list_chapters">
                <div class="list_chapters"><a href="/manga/test/1/">Chapitre 1</a></div>
                <div class="list_chapters"><span class="badge">RAW</span><a href="/manga/test/2/">Chapitre 2</a></div>
            </div>
        """.trimIndent())
        assertEquals(1, document.select(japscanChapterListSelector(hideSpoilers = true)).size)
        assertEquals(2, document.select(japscanChapterListSelector(hideSpoilers = false)).size)
    }

    @Test
    fun webtoonCaptureNeedsTheLongStripReaderAndImageContainers() {
        val readiness = readerReadyScript(isWebtoon = true)
        assertTrue(readiness.contains("window.__japscanHooked !== true"))
        assertTrue(readiness.contains("getElementById('full-reader')"))
        assertTrue(readiness.contains("querySelector('[id^=\"d-img-\"]')"))
        assertFalse(readiness.contains("document.readyState === 'complete'"))
    }

    @Test
    fun paginatedCaptureNeedsThePageReaderRatherThanTheHtmlLoadEvent() {
        val readiness = readerReadyScript(isWebtoon = false)
        assertTrue(readiness.contains("window.__japscanHooked !== true"))
        assertTrue(readiness.contains("getElementById('pages')"))
        assertTrue(readiness.contains("window.__japscanLastBlob"))
    }

    @Test
    fun paginatedJavascriptUsesExactlyTheRegisteredAndroidBridgeNames() {
        val script = paginatedDriver("testBridge")
        assertTrue(script.contains("window.testBridge_savePage.post(d)"))
        assertTrue(script.contains("window.testBridge_passDone.post(String(total))"))
        assertFalse(script.contains("window.\$testBridge_savePage"))
        assertFalse(script.contains("window.\$testBridge_passDone"))
    }

    @Test
    fun webtoonJavascriptUsesExactlyTheRegisteredAndroidBridgeNames() {
        val script = webtoonDriver("testBridge", "manhwa")
        assertTrue(script.contains("window.testBridge_savePage.post(uri)"))
        assertTrue(script.contains("window.testBridge_passDone.post("))
        assertFalse(script.contains("__JAPSCAN_BRIDGE__"))
    }

    @Test
    fun captureBridgesReportExpectedPageCounts() {
        assertTrue(paginatedDriver("testBridge").contains("passDone.post(String(total))"))
        assertTrue(webtoonDriver("testBridge", "manhwa").contains("passDone.post(String(Math.max(tilesExpected, tilesEmitted)))"))
    }
}

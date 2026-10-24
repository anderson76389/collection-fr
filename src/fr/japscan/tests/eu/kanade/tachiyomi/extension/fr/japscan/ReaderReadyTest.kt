package eu.kanade.tachiyomi.extension.fr.japscan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadyTest {
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
    fun captureBridgesReportExpectedPageCounts() {
        assertTrue(paginatedDriver("testBridge").contains("passDone.post(String(total))"))
        assertTrue(webtoonDriver("testBridge", "manhwa").contains("passDone.post(String(Math.max(tilesExpected, tilesEmitted)))"))
    }
}

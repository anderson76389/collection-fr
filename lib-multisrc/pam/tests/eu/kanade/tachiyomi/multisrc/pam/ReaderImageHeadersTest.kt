package eu.kanade.tachiyomi.multisrc.pam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderImageHeadersTest {
    @Test
    fun nativeImageRequestsCarryChapterRefererAndReaderAuthorization() {
        val url = "https://epsilonsoft.to/serie/exemple/chapter/chapitre-1"
        val headers = pamReaderImageHeaders(
            mapOf(
                "X-Page-Token" to "secret-token",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://epsilonsoft.to/library",
            ),
            url,
        )
        assertEquals(url, headers["Referer"])
        assertEquals("secret-token", headers["X-Page-Token"])
        assertNull(headers["X-Requested-With"])
    }

    @Test
    fun corruptedHeaderValuesCannotBreakOkHttpRequests() {
        val headers = pamReaderImageHeaders(
            mapOf("X-Page-Token" to "bad\r\nX-Forged: yes"),
            "https://epsilonsoft.to/serie/exemple/chapter/1",
        )
        assertNull(headers["X-Page-Token"])
        assertNull(headers["X-Forged"])
    }
}

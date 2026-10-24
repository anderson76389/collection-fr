package eu.kanade.tachiyomi.extension.fr.japscan

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageIssueTest {
    @Test
    fun cloudflareChallengeAndAccessDeniedAreNotReportedAsMissingPages() {
        for (title in listOf(
            "\"Just a moment...\"",
            "\"Attention Required! | Cloudflare\"",
            "\"Sorry, you have been blocked\"",
            "\"403 Forbidden\"",
        )) {
            assertTrue(japscanReaderPageIssue(title)?.contains("Cloudflare") == true)
        }
    }

    @Test
    fun realNotFoundPagesAreDistinctFromCloudflareErrors() {
        assertTrue(japscanReaderPageIssue("\"404 Not Found\"")?.contains("(404)") == true)
        assertTrue(japscanReaderPageIssue("\"Not Found\"")?.contains("(404)") == true)
    }

    @Test
    fun regularChapterTitlesAreNeverBlockedJustForContainingANumber() {
        assertNull(japscanReaderPageIssue("\"Japscan | Chapitre 404\""))
        assertNull(japscanReaderPageIssue("\"Lecture - Chapitre 115\""))
        assertNull(japscanReaderPageIssue("\"\""))
    }
}

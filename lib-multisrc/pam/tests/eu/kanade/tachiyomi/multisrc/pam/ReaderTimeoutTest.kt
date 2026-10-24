package eu.kanade.tachiyomi.multisrc.pam

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTimeoutTest {
    @Test
    fun cloudflareOrMissingPageIsDifferentFromReaderFailure() {
        assertTrue(pamReaderTimeoutReason(false, false, false, false).contains("Cloudflare"))
    }

    @Test
    fun missingManifestOrKeyIsReported() {
        assertTrue(pamReaderTimeoutReason(true, true, false, false).contains("manifeste"))
    }

    @Test
    fun missingImageRequestIsReported() {
        assertTrue(pamReaderTimeoutReason(true, true, true, false).contains("aucune requête"))
    }

    @Test
    fun imageHeaderMismatchIsReported() {
        assertTrue(pamReaderTimeoutReason(true, true, true, true).contains("en-têtes"))
    }
}

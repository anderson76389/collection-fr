package eu.kanade.tachiyomi.multisrc.pam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReaderFallbackTest {
    @Test
    fun otherPamSourcesNeverUseMissingImageRequestFallback() {
        assertFalse(pamReaderFallbackAllowed(enabled = false, wait = 60.seconds))
    }

    @Test
    fun optedInSoftEpsilonWaitsBrieflyForRealImageHeaders() {
        assertFalse(pamReaderFallbackAllowed(enabled = true, wait = 100.milliseconds))
        assertFalse(pamReaderFallbackAllowed(enabled = true, wait = 1999.milliseconds))
        assertTrue(pamReaderFallbackAllowed(enabled = true, wait = 2.seconds))
    }
}

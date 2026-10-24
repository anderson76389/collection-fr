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
    @Test
    fun navigationRetryIsLimitedToSoftEpsilonAndOccursOnce() {
        assertFalse(pamReaderNavigationRetryAllowed(false, true, true, false, false, 20.seconds))
        assertFalse(pamReaderNavigationRetryAllowed(true, false, true, false, false, 20.seconds))
        assertFalse(pamReaderNavigationRetryAllowed(true, true, true, true, false, 20.seconds))
        assertFalse(pamReaderNavigationRetryAllowed(true, true, true, false, true, 20.seconds))
        assertFalse(pamReaderNavigationRetryAllowed(true, true, true, false, false, 17.seconds))
        assertTrue(pamReaderNavigationRetryAllowed(true, true, true, false, false, 18.seconds))
    }

}

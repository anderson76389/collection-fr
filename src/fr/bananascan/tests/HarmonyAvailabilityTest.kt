package eu.kanade.tachiyomi.extension.fr.bananascan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonyAvailabilityTest {
    @Test
    fun offlineErrorsAreRecognized() {
        assertTrue(harmonyServerIsUnavailable(502))
        assertTrue(harmonyServerIsUnavailable(503))
        assertTrue(harmonyServerIsUnavailable(504))
    }

    @Test
    fun expectedResponsesAreNotMisclassified() {
        assertFalse(harmonyServerIsUnavailable(200))
        assertFalse(harmonyServerIsUnavailable(403))
        assertFalse(harmonyServerIsUnavailable(404))
    }
}

package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFailureTest {
    @Test
    fun incompatibleDeviceIsNotMisreportedAsMissingPages() {
        for (reason in listOf(
            "Lecture impossible sur cet appareil",
            "lecteur non compatible",
            "Incompatible device",
        )) {
            assertTrue(softEpsilonReaderFailure(reason).contains("refuse ce lecteur"))
        }
    }

    @Test
    fun canvasRestrictionIsExplainedWithoutPretendingToBypassIt() {
        assertTrue(softEpsilonReaderFailure("Canvas is blocked").contains("refuse l’export"))
    }

    @Test
    fun serverAccessAndMissingChapterAreDistinct() {
        assertTrue(softEpsilonReaderFailure("HTTP error 403").contains("vérification"))
        assertTrue(softEpsilonReaderFailure("HTTP error 429").contains("limitation"))
        assertTrue(softEpsilonReaderFailure("HTTP error 404").contains("introuvable"))
    }

    @Test
    fun oldReaderTimeoutBecomesActionable() {
        assertTrue(softEpsilonReaderFailure("The site's reader did not load").contains("lecteur v2"))
        assertTrue(softEpsilonReaderFailure("Unexpected reader result").contains("lecteur v2"))
    }

    @Test
    fun unknownErrorIsPreservedForDiagnosis() {
        assertTrue(softEpsilonReaderFailure("Unexpected test error").contains("Unexpected test error"))
    }
}

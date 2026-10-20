package eu.kanade.tachiyomi.extension.fr.japscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun onlyReaderFilesInCacheAreServed() {
        val cache = temp.newFolder("cache")
        val page = java.io.File(cache, "japscan-session-0.bin").apply { writeText("page") }
        assertEquals(page.canonicalFile, readerCacheFile(cache, page.path))
        assertNull(readerCacheFile(cache, temp.newFile("japscan-outside.bin").path))
        assertNull(readerCacheFile(cache, java.io.File(cache, "../japscan-outside.bin").path))
        assertNull(readerCacheFile(cache, java.io.File(cache, "preferences.xml").apply { writeText("private") }.path))
    }
}

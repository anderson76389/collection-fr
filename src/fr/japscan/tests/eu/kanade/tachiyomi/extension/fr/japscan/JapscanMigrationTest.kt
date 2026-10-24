package eu.kanade.tachiyomi.extension.fr.japscan

import org.junit.Assert.assertEquals
import org.junit.Test

class JapscanMigrationTest {
    @Test
    fun oldMangaLibraryPathsArePreserved() {
        assertEquals("/manga/one-piece/", japscanMangaPath("/manga/one-piece/"))
        assertEquals("/manga/one-piece/", japscanMangaPath("https://www.japscan.foo/manga/one-piece/"))
        assertEquals("/manhwa/solo-leveling/", japscanMangaPath("https://japscan.st/manhwa/solo-leveling/"))
    }

    @Test
    fun readerPageLinksNormalizeToExistingChapterIdentity() {
        assertEquals("/manga/one-piece/1135/", japscanChapterPath("/manga/one-piece/1135/"))
        assertEquals("/manga/one-piece/1135/", japscanChapterPath("/manga/one-piece/1135/1"))
        assertEquals("/manga/one-piece/1135/", japscanChapterPath("https://japscan.st/manga/one-piece/1135/5"))
        assertEquals("/manhwa/solo-leveling/77/", japscanChapterPath("/manhwa/solo-leveling/77/2?from=list"))
    }
}

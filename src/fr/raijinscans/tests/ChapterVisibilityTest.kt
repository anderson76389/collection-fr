package eu.kanade.tachiyomi.extension.fr.raijinscans

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterVisibilityTest {
    private fun chapter(
        isPremium: Boolean? = null,
        isLocked: Boolean? = null,
        isFree: Boolean? = null,
        access: String? = null,
    ) = ChapterData(
        id = "chapter-id",
        number = "330",
        title = "Chapitre disponible",
        updatedAt = "2026-10-20T12:00:00.000Z",
        isPremium = isPremium,
        isLocked = isLocked,
        isFree = isFree,
        access = access,
    )

    @Test
    fun explicitlyPremiumOrLockedChaptersAreHidden() {
        assertTrue(chapter(isPremium = true).isExplicitlyLocked())
        assertTrue(chapter(isLocked = true).isExplicitlyLocked())
        assertTrue(chapter(isFree = false).isExplicitlyLocked())
        assertTrue(chapter(access = "PREMIUM").isExplicitlyLocked())
        assertTrue(chapter(access = "subscription").isExplicitlyLocked())
    }

    @Test
    fun publicChaptersAndUnknownAccessAreNotDiscarded() {
        assertFalse(chapter().isExplicitlyLocked())
        assertFalse(chapter(isFree = true).isExplicitlyLocked())
        assertFalse(chapter(isPremium = false, isLocked = false).isExplicitlyLocked())
        assertFalse(chapter(access = "public").isExplicitlyLocked())
    }
}

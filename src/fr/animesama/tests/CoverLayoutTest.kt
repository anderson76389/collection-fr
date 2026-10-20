package eu.kanade.tachiyomi.extension.fr.animesama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLayoutTest {
    @Test
    fun landscapeOriginalIsFittedWithoutCroppingOrUpscaling() {
        val layout = coverLayout(2438, 1369)
        assertEquals(1000, layout.width)
        assertEquals(1500, layout.height)
        assertEquals(0f, layout.left, 0.01f)
        assertTrue(layout.top > 0)
        assertEquals(2438f / 1369f, layout.imageWidth / layout.imageHeight, 0.001f)
        assertTrue(layout.top + layout.imageHeight <= layout.height)
    }

    @Test
    fun portraitOriginalKeepsItsFullFrameAndSize() {
        val layout = coverLayout(600, 900)
        assertEquals(600, layout.width)
        assertEquals(900, layout.height)
        assertEquals(0f, layout.left, 0.001f)
        assertEquals(0f, layout.top, 0.001f)
    }

    @Test
    fun tallOriginalAddsSideSpaceInsteadOfCuttingTheTopAndBottom() {
        val layout = coverLayout(600, 1200)
        assertEquals(800, layout.width)
        assertEquals(1200, layout.height)
        assertEquals(100f, layout.left, 0.01f)
        assertEquals(0f, layout.top, 0.01f)
    }
}

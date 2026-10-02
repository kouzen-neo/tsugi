package com.kouzenneo.tsugi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimTest {

    /** In-memory image: [bg] everywhere, with an optional content rectangle painted in [ink]. */
    private class Fake(
        override val width: Int,
        override val height: Int,
        private val bg: Int = 0xFFFFFF,
        private val content: Box? = null,
        private val ink: Int = 0x101010,
    ) : Pixmap {
        private val pixels = IntArray(width * height) { bg }
        init {
            content?.let { fill(it) }
        }

        private fun fill(box: Box) {
            for (y in box.top until box.bottom) {
                for (x in box.left until box.right) {
                    pixels[y * width + x] = ink
                }
            }
        }

        override fun pixel(x: Int, y: Int): Int = pixels[y * width + x]
    }

    @Test
    fun `blank image has no content box`() {
        assertNull(Trim.blankBox(Fake(200, 100)))
    }

    @Test
    fun `content box hugs the drawn pixels`() {
        val p = Fake(200, 100, content = Box(20, 10, 180, 60))
        val box = Trim.blankBox(p)!!

        assertEquals(20, box.left)
        assertEquals(10, box.top)
        assertEquals(180, box.right)
        assertEquals(60, box.bottom)
        assertEquals(160, box.width)
        assertEquals(50, box.height)
    }

    @Test
    fun `near uniform noise is still treated as background`() {
        val p = Fake(200, 100, bg = 0xFFFFFF, content = Box(50, 40, 150, 90), ink = 0xF8F8F8)
        assertNull("a 2-value difference must not register as content", Trim.blankBox(p))
    }

    @Test
    fun `speck far from the background is content`() {
        val p = Fake(200, 100, bg = 0xFFFFFF, content = Box(50, 40, 150, 90), ink = 0x101010)
        assertEquals(Box(50, 40, 150, 90), Trim.blankBox(p))
    }

    @Test
    fun `uniform box is the intersection so a stack shares one edge`() {
        val maps = listOf(
            Fake(200, 100, content = Box(20, 10, 180, 60)),
            Fake(200, 100, content = Box(20, 10, 200, 95)),
            Fake(200, 100, content = Box(24, 16, 170, 70)),
        )
        val box = Trim.uniformBox(maps)!!

        assertEquals(Box(24, 16, 170, 60), box)
    }

    @Test
    fun `uniform box is rejected when the shared margin would eat the image`() {
        val maps = listOf(
            Fake(200, 100, content = Box(20, 10, 180, 60)),
            Fake(200, 100, content = Box(20, 55, 180, 80)),
        )
        assertNull(Trim.uniformBox(maps))
    }

    @Test
    fun `uniform box is rejected when any image is blank`() {
        val maps = listOf(Fake(200, 100, content = Box(20, 10, 180, 60)), Fake(200, 100))
        assertNull(Trim.uniformBox(maps))
    }

    @Test
    fun `uniform box of nothing is null`() {
        assertNull(Trim.uniformBox(emptyList()))
    }

    @Test
    fun `content smaller than the minimum keep threshold is discarded`() {
        val p = Fake(200, 100, content = Box(100, 50, 104, 53))
        assertNull(Trim.blankBox(p))
    }
}

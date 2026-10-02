package com.kouzenneo.tsugi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawMapperTest {

    private val src = Box(0, 0, 1000, 500)
    private val box = Placed("a", 100, 200, 400, 400)

    @Test
    fun `contain letterboxes and centres inside the box`() {
        val d = DrawMapper.map("a", src, box, FitMode.CONTAIN, 1f)

        assertEquals(400, d.dstW)
        assertEquals(200, d.dstH)
        assertEquals(100, d.dstX)
        assertEquals(300, d.dstY)
        assertEquals(1000, d.srcW)
        assertEquals(500, d.srcH)
    }

    @Test
    fun `cover fills the box and crops the overflowing axis`() {
        val d = DrawMapper.map("a", src, box, FitMode.COVER, 1f)

        assertEquals(400, d.dstW)
        assertEquals(400, d.dstH)
        assertEquals(500, d.srcW) // 1000 wide source cropped to square aspect
        assertEquals(500, d.srcH)
        assertEquals(250, d.srcX) // centred crop
        assertEquals(0, d.srcY)
    }

    @Test
    fun `source rect stays inside the trimmed box when zoomed in`() {
        val d = DrawMapper.map("a", src, box, FitMode.CONTAIN, 4f)

        assertTrue(d.srcX >= src.left)
        assertTrue(d.srcY >= src.top)
        assertTrue(d.srcX + d.srcW <= src.right)
        assertTrue(d.srcY + d.srcH <= src.bottom)
        assertEquals(250, d.srcW)
        assertEquals(125, d.srcH)
    }

    @Test
    fun `zoomed out shows the whole source again`() {
        val d = DrawMapper.map("a", src, box, FitMode.CONTAIN, 0.5f)

        assertEquals(src.width, d.srcW)
        assertEquals(src.height, d.srcH)
        assertEquals(src.left, d.srcX)
    }

    @Test
    fun `contain on a box matching the aspect is an exact fit`() {
        val d = DrawMapper.map("a", src, Placed("a", 0, 0, 800, 400), FitMode.CONTAIN, 1f)

        assertEquals(0, d.dstX)
        assertEquals(0, d.dstY)
        assertEquals(800, d.dstW)
        assertEquals(400, d.dstH)
    }
}


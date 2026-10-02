package com.kouzenneo.tsugi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {

    private fun cfg(
        axis: AxisMode = AxisMode.VERTICAL,
        columns: Int = 2,
        gap: Int = 0,
        padding: Int = 0,
        sizeMode: SizeMode = SizeMode.FIT_WIDTH,
        target: Int = 1080,
        hAlign: HAlign = HAlign.CENTER,
        vAlign: VAlign = VAlign.TOP,
    ) = LayoutConfig(
        axis = axis,
        columns = columns,
        gap = gap,
        padding = padding,
        sizeMode = sizeMode,
        target = target,
        hAlign = hAlign,
        vAlign = vAlign,
    )

    @Test
    fun `no items yields empty result`() {
        val r = LayoutEngine.compute(cfg(), emptyList())
        assertTrue(r.isEmpty)
        assertEquals(0, r.width)
        assertEquals(0, r.height)
    }

    @Test
    fun `fit width gives every item the same width and keeps aspect ratio`() {
        val items = listOf(
            SourceSize("a", 1080, 2400),
            SourceSize("b", 1080, 1200),
        )
        val r = LayoutEngine.compute(cfg(target = 540), items)

        assertEquals(540, r.placements[0].width)
        assertEquals(540, r.placements[1].width)
        assertEquals(1200, r.placements[0].height) // half of 2400
        assertEquals(600, r.placements[1].height) // half of 1200
        assertEquals(540, r.width)
        assertEquals(1800, r.height)
    }

    @Test
    fun `vertical stack separates items by exactly the gap`() {
        val items = listOf(SourceSize("a", 100, 100), SourceSize("b", 100, 100), SourceSize("c", 100, 100))
        val r = LayoutEngine.compute(cfg(sizeMode = SizeMode.NATIVE, gap = 12), items)

        assertEquals(0, r.placements[0].top)
        assertEquals(112, r.placements[1].top)
        assertEquals(224, r.placements[2].top)
        assertEquals(100 * 3 + 12 * 2, r.height)
    }

    @Test
    fun `padding insets the canvas on every side`() {
        val items = listOf(SourceSize("a", 100, 50), SourceSize("b", 100, 50))
        val r = LayoutEngine.compute(cfg(sizeMode = SizeMode.NATIVE, gap = 4, padding = 20), items)

        assertEquals(140, r.width)
        assertEquals(20 + 50 + 4 + 50 + 20, r.height)
        assertEquals(20, r.placements[0].left)
        assertEquals(20, r.placements[0].top)
        assertEquals(20, r.placements[1].left)
        assertEquals(74, r.placements[1].top)
    }

    @Test
    fun `mixed native sizes align inside the widest column`() {
        val items = listOf(SourceSize("a", 200, 50), SourceSize("b", 100, 50))
        val r = LayoutEngine.compute(cfg(sizeMode = SizeMode.NATIVE, hAlign = HAlign.CENTER), items)

        assertEquals(200, r.width)
        assertEquals(0, r.placements[0].left)
        assertEquals(50, r.placements[1].left) // (200-100)/2
    }

    @Test
    fun `align start and end shift within the cell`() {
        val items = listOf(SourceSize("a", 200, 50), SourceSize("b", 100, 50))
        val start = LayoutEngine.compute(cfg(sizeMode = SizeMode.NATIVE, hAlign = HAlign.START), items)
        val end = LayoutEngine.compute(cfg(sizeMode = SizeMode.NATIVE, hAlign = HAlign.END), items)

        assertEquals(0, start.placements[1].left)
        assertEquals(100, end.placements[1].left)
    }

    @Test
    fun `grid fills row major with uniform cells`() {
        val items = (1..5).map { SourceSize("i$it", 100, 80) }
        val r = LayoutEngine.compute(
            cfg(axis = AxisMode.GRID, columns = 3, sizeMode = SizeMode.NATIVE, gap = 6, padding = 2),
            items,
        )

        assertEquals(2 + 100 * 3 + 6 * 2 + 2, r.width)
        assertEquals(2 + 80 * 2 + 6 + 2, r.height)
        assertEquals(2, r.placements[0].left)
        assertEquals(108, r.placements[1].left)
        assertEquals(214, r.placements[2].left)
        assertEquals(88, r.placements[3].top)
        assertEquals(88, r.placements[4].top)
    }

    @Test
    fun `fixed size puts every item in a square box`() {
        val items = listOf(SourceSize("a", 4000, 300), SourceSize("b", 500, 4000))
        val r = LayoutEngine.compute(cfg(sizeMode = SizeMode.FIXED, target = 200), items)

        assertTrue(r.placements.all { it.width == 200 && it.height == 200 })
        assertEquals(200, r.width)
        assertEquals(400, r.height)
    }

    @Test
    fun `per item scale overrides the uniform width only in scaled modes`() {
        val items = listOf(SourceSize("a", 1000, 500), SourceSize("b", 1000, 500))
        val r = LayoutEngine.compute(
            cfg(sizeMode = SizeMode.NATIVE),
            items,
            scales = mapOf("b" to 0.5f),
        )

        assertEquals(500, r.placements[0].height)
        assertEquals(250, r.placements[1].height)
    }

    @Test
    fun `per item scale is clamped to a usable range`() {
        val items = listOf(SourceSize("a", 1000, 500), SourceSize("b", 1000, 500))
        val r = LayoutEngine.compute(
            cfg(sizeMode = SizeMode.NATIVE),
            items,
            scales = mapOf("a" to 0f, "b" to 1000f),
        )

        assertTrue(r.placements.all { it.width in 1..8000 && it.height in 1..4000 })
    }

    @Test
    fun `oversized result is shrunk instead of overflowing canvas limits`() {
        val items = List(200) { SourceSize("i$it", 1080, 2400) }
        val r = LayoutEngine.compute(cfg(target = 1080, gap = 0), items)

        assertTrue("width ${r.width} must respect MAX_DIM", r.width <= LayoutEngine.MAX_DIM)
        assertTrue("height ${r.height} must respect MAX_DIM", r.height <= LayoutEngine.MAX_DIM)
        assertTrue(r.shrink < 1f)
        assertTrue(r.placements.all { it.width <= r.width && it.height <= r.height })
    }

    @Test
    fun `horizontal strip advances along x and aligns vertically`() {
        val items = listOf(SourceSize("a", 100, 200), SourceSize("b", 100, 50))
        val r = LayoutEngine.compute(
            cfg(axis = AxisMode.HORIZONTAL, sizeMode = SizeMode.NATIVE, gap = 10, vAlign = VAlign.BOTTOM),
            items,
        )

        assertEquals(0, r.placements[0].left)
        assertEquals(110, r.placements[1].left)
        assertEquals(0, r.placements[0].top)
        assertEquals(150, r.placements[1].top) // 200 - 50
        assertEquals(210, r.width)
        assertEquals(200, r.height)
    }
}

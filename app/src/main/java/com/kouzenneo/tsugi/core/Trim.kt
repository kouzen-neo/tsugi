package com.kouzenneo.tsugi.core

import kotlin.math.max
import kotlin.math.min

/** Read-only pixel access, so trimming is testable without Android bitmaps. */
interface Pixmap {
    val width: Int
    val height: Int
    fun pixel(x: Int, y: Int): Int
}

/** Half-open pixel rectangle: [left, right) x [top, bottom). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isValid: Boolean get() = width > 0 && height > 0

    fun intersect(o: Box): Box =
        Box(max(left, o.left), max(top, o.top), min(right, o.right), min(bottom, o.bottom))

    companion object {
        fun full(w: Int, h: Int) = Box(0, 0, w, h)
    }
}

/**
 * Dead-margin removal for screenshots.
 *
 * Screenshot stacks look wrong for two reasons: every shot carries the same uniform
 * margin (status bar, nav bar), and each shot ends with an arbitrary amount of empty
 * background. Both are detected by treating the corner colour as background.
 *
 * [blankBox] trims one image; [uniformBox] intersects those results across a set so a
 * whole stack loses the exact same margin and its edges line up.
 */
object Trim {

    /** A crop smaller than this is treated as noise, not content. */
    const val MIN_KEEP = 8

    private const val DEFAULT_TOLERANCE = 24

    /**
     * Bounding box of everything that is not background, or null when the image is
     * blank or its content is too small to be worth keeping.
     */
    fun blankBox(p: Pixmap, tolerance: Int = DEFAULT_TOLERANCE): Box? {
        val w = p.width
        val h = p.height
        if (w <= 0 || h <= 0) return null

        val bg = cornerColor(p)
        val top = (0 until h).firstOrNull { !rowBlank(p, it, bg, tolerance) } ?: return null
        val bottom = (h - 1 downTo top)
            .firstOrNull { !rowBlank(p, it, bg, tolerance) } ?: return null
        val left = (0 until w)
            .firstOrNull { !colBlank(p, it, bg, tolerance, top, bottom) } ?: return null
        val right = (w - 1 downTo left)
            .firstOrNull { !colBlank(p, it, bg, tolerance, top, bottom) } ?: return null

        val box = Box(left, top, right + 1, bottom + 1)
        return if (box.width >= MIN_KEEP && box.height >= MIN_KEEP) box else null
    }

    /**
     * The margin every image in the set can lose without losing content, so the stack
     * shares one clean edge. Null when the intersection is too small to be meaningful
     * (images of unrelated sizes), in which case callers fall back to no trimming.
     */
    fun uniformBox(maps: List<Pixmap>, tolerance: Int = DEFAULT_TOLERANCE): Box? {
        if (maps.isEmpty()) return null
        var acc: Box? = null
        for (p in maps) {
            val box = blankBox(p, tolerance) ?: return null
            acc = acc?.intersect(box) ?: box
        }
        val merged = acc ?: return null
        return if (merged.width >= MIN_KEEP && merged.height >= MIN_KEEP) merged else null
    }

    private fun rowBlank(p: Pixmap, y: Int, bg: Int, tol: Int): Boolean {
        for (x in 0 until p.width) if (!near(p.pixel(x, y), bg, tol)) return false
        return true
    }

    private fun colBlank(p: Pixmap, x: Int, bg: Int, tol: Int, fromY: Int, toY: Int): Boolean {
        for (y in fromY..toY) if (!near(p.pixel(x, y), bg, tol)) return false
        return true
    }

    private fun cornerColor(p: Pixmap): Int {
        val x = p.width - 1
        val y = p.height - 1
        val corners = intArrayOf(p.pixel(0, 0), p.pixel(x, 0), p.pixel(0, y), p.pixel(x, y))
        var r = 0
        var g = 0
        var b = 0
        for (c in corners) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        return (r / 4 shl 16) or (g / 4 shl 8) or (b / 4)
    }

    private fun near(c: Int, ref: Int, tol: Int): Boolean {
        val dr = ((c shr 16) and 0xFF) - ((ref shr 16) and 0xFF)
        val dg = ((c shr 8) and 0xFF) - ((ref shr 8) and 0xFF)
        val db = (c and 0xFF) - (ref and 0xFF)
        return dr <= tol && dr >= -tol && dg <= tol && dg >= -tol && db <= tol && db >= -tol
    }
}

package com.kouzenneo.tsugi.render

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Cheap blurred backdrop for the BLUR background mode.
 *
 * The source is crushed to a few dozen pixels, box-blurred there, then stretched back
 * out by the renderer. At that size the cost is negligible and the result is the soft
 * colour wash people expect from a blur background, without a real convolution pass.
 */
object Backdrop {

    private const val MAX_SIDE = 48

    fun prepare(source: Bitmap): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val scale = min(1f, MAX_SIDE.toFloat() / max(source.width, source.height))
        val w = max(8, (source.width * scale).toInt())
        val h = max(8, (source.height * scale).toInt())

        val small = Bitmap.createScaledBitmap(source, w, h, true)
        if (small === source) return null

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        boxBlur(pixels, w, h, maxOf(2, minOf(w, h) / 8))
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }

    /** Two-pass running-sum box blur over the ARGB channels. */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val r = radius.coerceIn(1, minOf(w, h) / 2)
        if (r < 1) return
        val window = r * 2 + 1
        val line = IntArray(maxOf(w, h))

        // Horizontal pass: per row.
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) line[x] = pixels[offset + x]
            blurLine(line, w, r, window, pixels, offset, 1)
        }
        // Vertical pass: per column.
        for (x in 0 until w) {
            for (y in 0 until h) line[y] = pixels[y * w + x]
            blurLine(line, h, r, window, pixels, x, w)
        }
    }

    private fun blurLine(
        line: IntArray,
        length: Int,
        r: Int,
        window: Int,
        out: IntArray,
        outOffset: Int,
        stride: Int,
    ) {
        var sumA = 0L
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        // Prime the running sum with the left edge clamped to the first pixel.
        for (i in 0..r) {
            val c = line[minOf(i, length - 1)]
            sumA += (c ushr 24) and 0xFF
            sumR += (c ushr 16) and 0xFF
            sumG += (c ushr 8) and 0xFF
            sumB += c and 0xFF
        }
        for (i in 0 until length) {
            val outC = (sumA / window).toInt() shl 24 or
                (sumR / window).toInt() shl 16 or
                (sumG / window).toInt() shl 8 or
                (sumB / window).toInt()
            out[outOffset + i * stride] = outC

            val addIndex = minOf(i + r + 1, length - 1)
            val removeIndex = maxOf(i - r, 0)
            val add = line[addIndex]
            val remove = line[removeIndex]
            sumA += ((add ushr 24) and 0xFF) - ((remove ushr 24) and 0xFF)
            sumR += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
            sumG += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
            sumB += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

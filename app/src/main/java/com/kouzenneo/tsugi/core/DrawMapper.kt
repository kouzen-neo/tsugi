package com.kouzenneo.tsugi.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Source and destination rectangles for one item, in pixels of the same output canvas.
 * The preview and the exporter both consume this, which is what keeps them identical.
 */
data class Draw(
    val id: String,
    val srcX: Int,
    val srcY: Int,
    val srcW: Int,
    val srcH: Int,
    val dstX: Int,
    val dstY: Int,
    val dstW: Int,
    val dstH: Int,
)

object DrawMapper {

    private const val MIN_ZOOM = 0.05f
    private const val MAX_ZOOM = 8f

    /**
     * Fits the trimmed source [src] into the layout box [box].
     *
     * [zoom] above 1 crops into the image around its centre, so a per-item scale override
     * behaves the same whether the box is letterboxed or filled. CONTAIN shrinks the
     * destination inside the box; COVER keeps the destination exactly the box and crops
     * the source instead, so rounded corners and cell dividers stay where the layout says.
     */
    fun map(id: String, src: Box, box: Placed, fit: FitMode, zoom: Float): Draw {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val fullW = max(1, src.width).toFloat()
        val fullH = max(1, src.height).toFloat()

        // Zooming in samples a smaller region; zooming out cannot invent pixels.
        val sw = (fullW / z).coerceIn(1f, fullW)
        val sh = (fullH / z).coerceIn(1f, fullH)
        val srcX = src.left + (fullW - sw) / 2f
        val srcY = src.top + (fullH - sh) / 2f

        val bw = max(1, box.width).toFloat()
        val bh = max(1, box.height).toFloat()

        if (fit == FitMode.CONTAIN) {
            val k = min(bw / sw, bh / sh)
            val dw = sw * k
            val dh = sh * k
            return Draw(
                id = id,
                srcX = srcX.roundToInt(),
                srcY = srcY.roundToInt(),
                srcW = sw.roundToInt().coerceAtLeast(1),
                srcH = sh.roundToInt().coerceAtLeast(1),
                dstX = (box.left + (bw - dw) / 2f).roundToInt(),
                dstY = (box.top + (bh - dh) / 2f).roundToInt(),
                dstW = dw.roundToInt().coerceAtLeast(1),
                dstH = dh.roundToInt().coerceAtLeast(1),
            )
        }

        val boxAspect = bw / bh
        val cw = if (sw / sh > boxAspect) sh * boxAspect else sw
        val ch = if (sw / sh > boxAspect) sh else sw / boxAspect
        return Draw(
            id = id,
            srcX = (srcX + (sw - cw) / 2f).roundToInt(),
            srcY = (srcY + (sh - ch) / 2f).roundToInt(),
            srcW = cw.roundToInt().coerceAtLeast(1),
            srcH = ch.roundToInt().coerceAtLeast(1),
            dstX = box.left,
            dstY = box.top,
            dstW = box.width,
            dstH = box.height,
        )
    }
}

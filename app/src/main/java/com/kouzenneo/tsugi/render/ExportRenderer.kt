package com.kouzenneo.tsugi.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.kouzenneo.tsugi.core.AxisMode
import com.kouzenneo.tsugi.core.BgMode

/**
 * Draws a [Scene] onto any [Canvas]. The editor preview and the exported file both go
 * through here, so the preview is not an approximation of the export — it is the same
 * draw calls at a different scale.
 *
 * @param scale output pixels -> device pixels. 1f for export.
 */
object ExportRenderer {

    /** Convenience for export: backdrop over the whole canvas, then the content. */
    fun draw(
        canvas: Canvas,
        scene: Scene,
        source: (String) -> Bitmap?,
        scale: Float = 1f,
    ) {
        if (scene.isEmpty) return
        val bounds = RectF(0f, 0f, scene.width * scale, scene.height * scale)
        drawBackdrop(canvas, scene, source, scale, bounds)
        drawItems(canvas, scene, source, scale)
    }

    /**
     * Background for [bounds] only, so a preview can paint it behind the scene without
     * flooding the whole viewport.
     */
    fun drawBackdrop(
        canvas: Canvas,
        scene: Scene,
        source: (String) -> Bitmap?,
        scale: Float,
        bounds: RectF,
    ) {
        when (scene.config.bgMode) {
            BgMode.TRANSPARENT -> Unit
            BgMode.COLOR -> canvas.drawRect(bounds, Paint().apply { color = scene.config.bgColor })
            BgMode.BLUR -> drawBlurredBackdrop(canvas, scene, source, bounds)
        }
    }

    fun drawItems(canvas: Canvas, scene: Scene, source: (String) -> Bitmap?, scale: Float) {
        if (scene.isEmpty) return
        val cfg = scene.config
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = cfg.cornerRadius * scale
        val clipPath = Path()

        for (item in scene.items) {
            val bitmap = source(item.id) ?: continue
            val d = item.draw
            val src = Rect(d.srcX, d.srcY, d.srcX + d.srcW, d.srcY + d.srcH)
            val dst = RectF(
                d.dstX * scale,
                d.dstY * scale,
                (d.dstX + d.dstW) * scale,
                (d.dstY + d.dstH) * scale,
            )

            val clipped = radius > 0f
            if (clipped) {
                canvas.save()
                clipPath.reset()
                clipPath.addRoundRect(dst, radius, radius, Path.Direction.CW)
                canvas.clipPath(clipPath)
            }
            canvas.drawBitmap(bitmap, src, dst, paint)
            if (clipped) canvas.restore()
        }

        if (cfg.separator) drawSeparators(canvas, scene, scale)
    }

    private fun drawBlurredBackdrop(
        canvas: Canvas,
        scene: Scene,
        source: (String) -> Bitmap?,
        bounds: RectF,
    ) {
        val first = scene.items.firstOrNull()?.let { source(it.id) }
        val backdrop = first?.let { Backdrop.prepare(it) }
        if (backdrop == null) {
            canvas.drawRect(bounds, Paint().apply { color = scene.config.bgColor })
            return
        }
        canvas.drawBitmap(backdrop, null, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
        // A touch of darkening keeps light content readable on top of its own blur.
        canvas.drawRect(bounds, Paint().apply { color = Color.argb(64, 0, 0, 0) })
        backdrop.recycle()
    }

    private fun drawSeparators(canvas: Canvas, scene: Scene, scale: Float) {
        val cfg = scene.config
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.separatorColor
            strokeWidth = maxOf(1f, cfg.separatorWidth * scale)
        }
        val width = scene.width * scale
        val height = scene.height * scale
        // The line belongs in the gap, not painted over the next image. With no gap it
        // lands exactly on the shared edge, which is what a divider means there.
        val inset = maxOf(0f, cfg.gap * scale / 2f)

        scene.items.forEachIndexed { index, item ->
            if (index == 0) return@forEachIndexed
            val d = item.draw
            when (cfg.axis) {
                AxisMode.VERTICAL -> {
                    val y = d.dstY * scale - inset
                    canvas.drawLine(0f, y, width, y, paint)
                }
                AxisMode.HORIZONTAL -> {
                    val x = d.dstX * scale - inset
                    canvas.drawLine(x, 0f, x, height, paint)
                }
                AxisMode.GRID -> {
                    if (index % cfg.columnsClamped != 0) {
                        val x = d.dstX * scale - inset
                        canvas.drawLine(x, 0f, x, height, paint)
                    }
                    if (index >= cfg.columnsClamped) {
                        val y = d.dstY * scale - inset
                        canvas.drawLine(0f, y, width, y, paint)
                    }
                }
            }
        }
    }
}

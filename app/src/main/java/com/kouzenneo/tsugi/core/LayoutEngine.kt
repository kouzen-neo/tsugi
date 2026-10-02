package com.kouzenneo.tsugi.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pixel dimensions of a source image, after trimming. */
data class SourceSize(val id: String, val width: Int, val height: Int)

/** Final destination rectangle of one item, in output pixels. */
data class Placed(val id: String, val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

data class LayoutResult(
    val width: Int,
    val height: Int,
    val placements: List<Placed>,
    /**
     * Uniform downscale applied because the raw layout exceeded the engine's size or
     * memory budget. Always 1f in the normal case; reported to the user rather than
     * silently cropping or failing.
     */
    val shrink: Float = 1f,
) {
    val isEmpty: Boolean get() = placements.isEmpty()
}

/**
 * Pure layout maths. No Android, no Compose — the same function drives the on-screen
 * preview and the exported file, so what you see is what you get.
 *
 * @param scales per-item zoom overrides; ids absent from the map use 1f.
 */
object LayoutEngine {
    /** Beyond this, canvas and encoder limits start to bite. */
    const val MAX_DIM = 24000

    /** Roughly 160 MB of ARGB_8888, the most a mid-range phone can allocate in one go. */
    const val MAX_PIXELS = 40_000_000f

    private const val MIN_BOX = 1

    fun compute(
        cfg: LayoutConfig,
        items: List<SourceSize>,
        scales: Map<String, Float> = emptyMap(),
    ): LayoutResult {
        if (items.isEmpty()) return LayoutResult(0, 0, emptyList())

        val boxes = items.map { boxOf(cfg, it.width, it.height, scales[it.id] ?: 1f) }
        val raw = when (cfg.axis) {
            AxisMode.VERTICAL -> vertical(cfg, items, boxes)
            AxisMode.HORIZONTAL -> horizontal(cfg, items, boxes)
            AxisMode.GRID -> grid(cfg, items, boxes)
        }
        return clampToMaxDim(raw)
    }

    /** Destination box an item occupies before [FitMode] letterboxing is applied. */
    private fun boxOf(cfg: LayoutConfig, srcW: Int, srcH: Int, scale: Float): Size {
        val s = scale.coerceIn(0.05f, 8f)
        val w = max(1, srcW).toFloat()
        val h = max(1, srcH).toFloat()
        val target = cfg.target.coerceIn(MIN_BOX, MAX_DIM)
        return when (cfg.sizeMode) {
            SizeMode.NATIVE -> Size(rnd(w * s), rnd(h * s))
            SizeMode.FIT_WIDTH -> Size(target, max(MIN_BOX, rnd(h * target / w * s)))
            SizeMode.FIT_HEIGHT -> Size(max(MIN_BOX, rnd(w * target / h * s)), target)
            SizeMode.FIXED, SizeMode.FILL -> Size(target, target)
        }
    }

    private fun vertical(
        cfg: LayoutConfig,
        items: List<SourceSize>,
        boxes: List<Size>,
    ): LayoutResult {
        val cellW = boxes.maxOf { it.w }
        val pad = max(0, cfg.padding)
        val gap = max(0, cfg.gap)
        val height = pad * 2 + boxes.sumOf { it.h } + gap * (boxes.size - 1)
        val width = pad * 2 + cellW

        var cursor = pad
        val placements = items.mapIndexed { i, src ->
            val b = boxes[i]
            val placed = Placed(
                id = src.id,
                left = pad + alignOffset(cfg.hAlign, cellW, b.w),
                top = cursor,
                width = b.w,
                height = b.h,
            )
            cursor += b.h + gap
            placed
        }
        return LayoutResult(width, max(0, height), placements)
    }

    private fun horizontal(
        cfg: LayoutConfig,
        items: List<SourceSize>,
        boxes: List<Size>,
    ): LayoutResult {
        val cellH = boxes.maxOf { it.h }
        val pad = max(0, cfg.padding)
        val gap = max(0, cfg.gap)
        val width = pad * 2 + boxes.sumOf { it.w } + gap * (boxes.size - 1)
        val height = pad * 2 + cellH

        var cursor = pad
        val placements = items.mapIndexed { i, src ->
            val b = boxes[i]
            val placed = Placed(
                id = src.id,
                left = cursor,
                top = pad + alignOffset(cfg.vAlign, cellH, b.h),
                width = b.w,
                height = b.h,
            )
            cursor += b.w + gap
            placed
        }
        return LayoutResult(max(0, width), height, placements)
    }

    private fun grid(
        cfg: LayoutConfig,
        items: List<SourceSize>,
        boxes: List<Size>,
    ): LayoutResult {
        val cols = cfg.columnsClamped
        val rows = (boxes.size + cols - 1) / cols
        val cellW = boxes.maxOf { it.w }
        val cellH = boxes.maxOf { it.h }
        val pad = max(0, cfg.padding)
        val gap = max(0, cfg.gap)

        val width = pad * 2 + cols * cellW + (cols - 1) * gap
        val height = pad * 2 + rows * cellH + (rows - 1) * gap

        val placements = items.mapIndexed { i, src ->
            val b = boxes[i]
            val row = i / cols
            val col = i % cols
            Placed(
                id = src.id,
                left = pad + col * (cellW + gap) + alignOffset(cfg.hAlign, cellW, b.w),
                top = pad + row * (cellH + gap) + alignOffset(cfg.vAlign, cellH, b.h),
                width = b.w,
                height = b.h,
            )
        }
        return LayoutResult(width, height, placements)
    }

    /**
     * Output has to fit in memory: one ARGB_8888 pixel is 4 bytes, so a 24k x 24k
     * canvas would be 2.2 GB. Both limits shrink the result uniformly and are
     * reported through [LayoutResult.shrink] instead of failing silently.
     */
    private fun clampToMaxDim(result: LayoutResult): LayoutResult {
        val bySide = MAX_DIM.toFloat() / max(result.width, result.height).toFloat()
        val byArea = sqrt(MAX_PIXELS / (result.width.toFloat() * result.height))
        val f = min(1f, min(bySide, byArea))
        if (f >= 1f) return result
        return LayoutResult(
            width = max(1, rnd(result.width * f)),
            height = max(1, rnd(result.height * f)),
            placements = result.placements.map {
                Placed(
                    id = it.id,
                    left = rnd(it.left * f),
                    top = rnd(it.top * f),
                    width = max(1, rnd(it.width * f)),
                    height = max(1, rnd(it.height * f)),
                )
            },
            shrink = f,
        )
    }

    private fun alignOffset(align: HAlign, cell: Int, size: Int): Int = when (align) {
        HAlign.START -> 0
        HAlign.CENTER -> (cell - size) / 2
        HAlign.END -> cell - size
    }

    private fun alignOffset(align: VAlign, cell: Int, size: Int): Int = when (align) {
        VAlign.TOP -> 0
        VAlign.CENTER -> (cell - size) / 2
        VAlign.BOTTOM -> cell - size
    }

    private fun rnd(v: Float): Int = max(MIN_BOX, min(v.roundToInt(), MAX_DIM))

    private data class Size(val w: Int, val h: Int)
}

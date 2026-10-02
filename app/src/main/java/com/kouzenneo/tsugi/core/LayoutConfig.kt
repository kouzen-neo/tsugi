package com.kouzenneo.tsugi.core

/** How items are arranged on the canvas. */
enum class AxisMode { VERTICAL, HORIZONTAL, GRID }

/**
 * How each item's own size is derived.
 *
 * NATIVE    — keep source pixels (per-item scale still applies).
 * FIT_WIDTH — every item gets the same width, height follows each image's aspect ratio.
 * FIT_HEIGHT— every item gets the same height, width follows the aspect ratio.
 * FIXED     — every item lives in a fixed square box, letterboxed by [FitMode].
 * FILL      — every item fills a fixed square cell, cropping when necessary.
 */
enum class SizeMode { NATIVE, FIT_WIDTH, FIT_HEIGHT, FIXED, FILL }

enum class FitMode { CONTAIN, COVER }
enum class HAlign { START, CENTER, END }
enum class VAlign { TOP, CENTER, BOTTOM }
enum class BgMode { COLOR, TRANSPARENT, BLUR }

enum class OutFormat(val ext: String, val mime: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
}

/**
 * Every knob of the stitcher. Pure data: no Android types, so the layout engine stays
 * unit-testable and the whole editor state is a single immutable value for undo/redo.
 *
 * Defaults describe the common case: screenshots of identical size, stacked into one
 * long image, each keeping its own aspect ratio.
 */
data class LayoutConfig(
    val axis: AxisMode = AxisMode.VERTICAL,
    val columns: Int = 2,
    val gap: Int = 0,
    val padding: Int = 0,
    val sizeMode: SizeMode = SizeMode.FIT_WIDTH,
    val target: Int = 1080,
    val fit: FitMode = FitMode.CONTAIN,
    val hAlign: HAlign = HAlign.CENTER,
    val vAlign: VAlign = VAlign.TOP,
    val cornerRadius: Int = 0,
    val separator: Boolean = false,
    val separatorColor: Int = 0x40000000,
    val separatorWidth: Int = 2,
    val bgMode: BgMode = BgMode.COLOR,
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    val trimUniform: Boolean = true,
    val trimTail: Boolean = true,
    val format: OutFormat = OutFormat.JPEG,
    val quality: Int = 92,
) {
    val columnsClamped: Int get() = columns.coerceIn(1, MAX_COLUMNS)

    companion object {
        const val MAX_COLUMNS = 12
    }
}

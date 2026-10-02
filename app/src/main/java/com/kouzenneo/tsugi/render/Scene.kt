package com.kouzenneo.tsugi.render

import com.kouzenneo.tsugi.core.Box
import com.kouzenneo.tsugi.core.Draw
import com.kouzenneo.tsugi.core.LayoutConfig

/** One item of the finished image: which part of the source lands where. */
data class SceneItem(val id: String, val srcBox: Box, val draw: Draw)

/**
 * A resolved stitch: everything needed to draw the result, in output pixels.
 * The editor scales it for display; the exporter draws it 1:1. Same numbers either way.
 */
data class Scene(
    val width: Int,
    val height: Int,
    val items: List<SceneItem>,
    val config: LayoutConfig,
    val shrink: Float = 1f,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val EMPTY = Scene(0, 0, emptyList(), LayoutConfig())
    }
}

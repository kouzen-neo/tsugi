package com.kouzenneo.tsugi.data

import android.graphics.Bitmap
import com.kouzenneo.tsugi.core.Pixmap

/**
 * Bridges a bitmap to the pure [Pixmap] interface used by trim detection.
 *
 * Pixels are pulled once into a plain IntArray: the analyser scans the same rows and
 * columns several times, and per-call [Bitmap.getPixel] would dominate the cost.
 */
class BitmapPixmap(bitmap: Bitmap) : Pixmap {

    private val pixels = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    override fun pixel(x: Int, y: Int): Int = pixels[y * width + x]
}

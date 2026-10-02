package com.kouzenneo.tsugi.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Decodes content:// images at a bounded size and caches them by (uri, size).
 *
 * Two sizes are used by the app: a small one for the editor preview, and a large one
 * for export. Caching both would blow up on long screenshot stacks, so the cache is
 * byte-budgeted and the preview simply reloads on demand.
 */
class BitmapLoader(private val resolver: ContentResolver) {

    private val cache = object : LruCache<String, Bitmap>(budgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(uri: String, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        loadBlocking(uri, maxDim)
    }

    fun loadBlocking(uri: String, maxDim: Int): Bitmap? {
        val key = key(uri, maxDim)
        cache.get(key)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // inJustDecodeBounds fills the bounds and returns null by design, so the
        // stream itself is what must be non-null here.
        val probed = open(uri) ?: return null
        probed.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val oriented = orient(uri, decoded)
        cache.put(key, oriented)
        return oriented
    }

    fun displayName(uri: String): String = runCatching {
        resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }.getOrNull().orEmpty()

    private fun open(uri: String): InputStream? =
        runCatching { resolver.openInputStream(Uri.parse(uri)) }.getOrNull()

    private fun orient(uri: String, bitmap: Bitmap): Bitmap {
        val orientation = open(uri)?.use {
            runCatching {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        fun key(uri: String, maxDim: Int) = "$uri@$maxDim"

        fun budgetKb(): Int =
            (Runtime.getRuntime().maxMemory() / 1024 / 8).coerceIn(8 * 1024, 96 * 1024).toInt()

        fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxDim) {
                sample *= 2
                longest /= 2
            }
            return sample
        }
    }
}

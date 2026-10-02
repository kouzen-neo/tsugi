package com.kouzenneo.tsugi.render

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kouzenneo.tsugi.core.BgMode
import com.kouzenneo.tsugi.core.Box
import com.kouzenneo.tsugi.core.DrawMapper
import com.kouzenneo.tsugi.core.LayoutConfig
import com.kouzenneo.tsugi.core.LayoutEngine
import com.kouzenneo.tsugi.core.Project
import com.kouzenneo.tsugi.core.OutFormat
import com.kouzenneo.tsugi.core.SourceSize
import com.kouzenneo.tsugi.core.Trim
import com.kouzenneo.tsugi.data.BitmapLoader
import com.kouzenneo.tsugi.data.BitmapPixmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * Renders the project at full resolution and writes it out.
 *
 * Source bitmaps are decoded one at a time and, when the set no longer fits the memory
 * budget, released and re-decoded for the draw pass. That keeps a 40-screenshot stack
 * inside the same heap a 4-screenshot stack needs.
 */
class StitchExporter(private val context: Context, private val loader: BitmapLoader) {

    /**
     * Renders [project] to a bitmap sized in *output* pixels.
     *
     * @return the bitmap plus a note when the engine had to shrink the layout.
     */
    suspend fun render(project: Project): Rendered = withContext(Dispatchers.IO) {
        val cfg = project.config
        val bitmaps = arrayOfNulls<Bitmap>(project.photos.size)
        val dims = arrayOfNulls<IntArray>(project.photos.size)
        val boxes = arrayOfNulls<Box>(project.photos.size)
        var budget = MEMORY_BUDGET_BYTES

        project.photos.forEachIndexed { i, photo ->
            val bitmap = loader.loadBlocking(photo.uri, EXPORT_MAX_DIM)
            if (bitmap == null) {
                dims[i] = intArrayOf(1, 1)
                return@forEachIndexed
            }
            dims[i] = intArrayOf(bitmap.width, bitmap.height)
            boxes[i] = if (cfg.trimTail) Trim.blankBox(BitmapPixmap(bitmap)) else null
            if (bitmap.byteCount <= budget) {
                budget -= bitmap.byteCount
                bitmaps[i] = bitmap
            } else {
                bitmap.recycle()
            }
        }

        val present = boxes.filterNotNull()
        val uniform = if (cfg.trimUniform && present.size == project.photos.size && present.isNotEmpty()) {
            present.reduce { acc, box -> acc.intersect(box) }.takeIf { it.width >= Trim.MIN_KEEP && it.height >= Trim.MIN_KEEP }
        } else {
            null
        }

        val effective = project.photos.indices.map { i ->
            val full = Box.full(dims[i]?.get(0) ?: 1, dims[i]?.get(1) ?: 1)
            when {
                !cfg.trimTail -> full
                uniform != null -> uniform
                else -> boxes[i] ?: full
            }
        }

        val scales = project.photos.associate { it.id to it.scale }
        val sources = project.photos.mapIndexed { i, photo -> SourceSize(photo.id, effective[i].width, effective[i].height) }
        val layout = LayoutEngine.compute(cfg, sources, scales)
        val scene = Scene(
            width = layout.width,
            height = layout.height,
            items = layout.placements.mapIndexed { i, placed ->
                SceneItem(
                    id = placed.id,
                    srcBox = effective[i],
                    draw = DrawMapper.map(placed.id, effective[i], placed, cfg.fit, scales[placed.id] ?: 1f),
                )
            },
            config = cfg.forRender(),
            shrink = layout.shrink,
        )

        val output = Bitmap.createBitmap(layout.width.coerceAtLeast(1), layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val byId = project.photos.withIndex().associate { (i, photo) -> photo.id to i }
        ExportRenderer.draw(
            canvas = Canvas(output),
            scene = scene,
            source = { id ->
                val index = byId[id] ?: return@draw null
                bitmaps[index] ?: loader.loadBlocking(project.photos[index].uri, EXPORT_MAX_DIM)
            },
            scale = 1f,
        )
        bitmaps.forEach { if (it != null && !it.isRecycled) it.recycle() }

        Rendered(output, if (layout.shrink < 1f) "Output scaled down to fit memory." else null)
    }

    suspend fun save(project: Project, rendered: Rendered): Uri? = withContext(Dispatchers.IO) {
        val format = project.config.format
        val name = "Tsugi_${timestamp()}.${format.ext}"
        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Tsugi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
            uri?.also { target ->
                val ok = context.contentResolver.openOutputStream(target)?.use {
                    rendered.bitmap.compress(format.compressFormat(), project.config.quality, it)
                } ?: false
                if (!ok) {
                    context.contentResolver.delete(target, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(target, values, null, null)
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Tsugi",
            )
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val file = File(dir, name)
            file.outputStream().use {
                rendered.bitmap.compress(format.compressFormat(), project.config.quality, it)
            }
            var uri: Uri? = null
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mime)) { _, scanned ->
                uri = scanned
            }
            uri ?: Uri.fromFile(file)
        }
        saved?.let { takePersistableRead(context, it) }
        saved
    }

    fun shareIntent(uri: Uri, format: OutFormat): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private companion object {
        const val EXPORT_MAX_DIM = 4096
        const val MEMORY_BUDGET_BYTES = 96L * 1024 * 1024

        /** JPEG cannot store transparency, so a transparent export composites on the colour. */
        fun LayoutConfig.forRender(): LayoutConfig =
            if (format == OutFormat.JPEG && bgMode == BgMode.TRANSPARENT) {
                copy(bgMode = BgMode.COLOR)
            } else {
                this
            }

        fun takePersistableRead(context: Context, uri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}


/** A finished render plus anything the user needs to know about it. */
data class Rendered(val bitmap: Bitmap, val note: String?)

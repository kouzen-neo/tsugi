package com.kouzenneo.tsugi.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.kouzenneo.tsugi.core.AxisMode
import com.kouzenneo.tsugi.core.BgMode
import com.kouzenneo.tsugi.core.LayoutConfig
import com.kouzenneo.tsugi.core.OutFormat
import com.kouzenneo.tsugi.core.Photo
import com.kouzenneo.tsugi.core.Project
import com.kouzenneo.tsugi.core.SizeMode
import com.kouzenneo.tsugi.data.BitmapLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * End-to-end proof that the stitching pipeline produces the image it promises: real
 * PNGs are decoded through [BitmapLoader], trimmed, laid out, drawn with
 * [ExportRenderer] and read back pixel by pixel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StitchRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * A screenshot stand-in: a [contentWidth] x [contentHeight] block of [color] on
     * white, inset by [margin] and followed by [tail] rows of empty background — the
     * shape of a real phone screenshot with a status bar and a scroll gap at the end.
     */
    private fun registerShot(
        id: String,
        color: Int,
        width: Int = 200,
        height: Int = 300,
        margin: Int = 20,
        contentWidth: Int = 160,
        contentHeight: Int = 220,
        tail: Int = 40,
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(
                margin.toFloat(),
                margin.toFloat(),
                (margin + contentWidth).toFloat(),
                (margin + contentHeight).toFloat(),
                android.graphics.Paint().apply { this.color = color },
            )
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()

        val uri = Uri.parse("content://shots/$id")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun project(vararg photos: Pair<String, Uri>, cfg: LayoutConfig): Project = Project(
        photos = photos.map { (id, uri) -> Photo(id = id, uri = uri.toString(), name = "$id.png") },
        config = cfg,
    )

    private fun Bitmap.near(x: Int, y: Int, expected: Int, tolerance: Int = 6): Boolean {
        val c = getPixel(x, y)
        return Math.abs(Color.red(c) - Color.red(expected)) <= tolerance &&
            Math.abs(Color.green(c) - Color.green(expected)) <= tolerance &&
            Math.abs(Color.blue(c) - Color.blue(expected)) <= tolerance
    }

    @Test
    fun `stack drops the shared margin and the trailing gap`() = runTest {
        val red = Color.RED
        val green = Color.GREEN
        val blue = Color.BLUE
        val cfg = LayoutConfig(axis = AxisMode.VERTICAL, sizeMode = SizeMode.NATIVE)
        val exporter = StitchExporter(context, BitmapLoader(context.contentResolver))

        val out = exporter.render(
            project(
                "a" to registerShot("a", red),
                "b" to registerShot("b", green),
                "c" to registerShot("c", blue),
                cfg = cfg,
            ),
        ).bitmap

        // 200x300 shots trimmed to their shared 160x220 content block, stacked with no gap.
        assertEquals(160, out.width)
        assertEquals(220 * 3, out.height)
        assertTrue("first band", out.near(80, 110, red))
        assertTrue("second band", out.near(80, 330, green))
        assertTrue("third band", out.near(80, 550, blue))
    }

    @Test
    fun `no blank row survives between two stacked shots`() = runTest {
        val cfg = LayoutConfig(axis = AxisMode.VERTICAL, sizeMode = SizeMode.NATIVE)
        val exporter = StitchExporter(context, BitmapLoader(context.contentResolver))

        val out = exporter.render(
            project(
                "a" to registerShot("a", Color.RED),
                "b" to registerShot("b", Color.GREEN),
                cfg = cfg,
            ),
        ).bitmap

        // Last row of the first shot and first row of the second are adjacent content.
        assertTrue("boundary", out.near(80, 219, Color.RED))
        assertTrue("boundary", out.near(80, 220, Color.GREEN))
        // Content reaches both edges, so nothing was cropped horizontally.
        assertTrue("left edge", out.near(0, 110, Color.RED))
        assertTrue("right edge", out.near(out.width - 1, 110, Color.RED))
    }

    @Test
    fun `gap padding and separators are painted where the layout says`() = runTest {
        val cfg = LayoutConfig(
            axis = AxisMode.VERTICAL,
            sizeMode = SizeMode.NATIVE,
            gap = 10,
            padding = 5,
            separator = true,
            separatorColor = Color.BLACK,
            separatorWidth = 2,
            bgMode = BgMode.COLOR,
            bgColor = Color.WHITE,
        )
        val exporter = StitchExporter(context, BitmapLoader(context.contentResolver))

        val out = exporter.render(
            project(
                "a" to registerShot("a", Color.RED),
                "b" to registerShot("b", Color.GREEN),
                cfg = cfg,
            ),
        ).bitmap

        assertEquals(160 + 10, out.width)
        assertEquals(5 + 220 + 10 + 220 + 5, out.height)
        // Padding ring stays background.
        assertTrue("padding", out.near(2, 2, Color.WHITE))
        // The gap between the two shots carries the separator, not content.
        val gapTop = 5 + 220
        assertTrue("separator", out.near(80, gapTop + 5, Color.BLACK))
        // Content starts after the padding.
        assertTrue("content", out.near(80, gapTop + 20, Color.GREEN))
    }

    @Test
    fun `transparent background leaves the canvas untouched`() = runTest {
        val cfg = LayoutConfig(
            sizeMode = SizeMode.NATIVE,
            gap = 20,
            padding = 8,
            // JPEG cannot carry alpha, so transparency only survives in PNG.
            format = OutFormat.PNG,
            bgMode = BgMode.TRANSPARENT,
        )
        val exporter = StitchExporter(context, BitmapLoader(context.contentResolver))

        val out = exporter.render(
            project(
                "a" to registerShot("a", Color.RED),
                "b" to registerShot("b", Color.GREEN),
                cfg = cfg,
            ),
        ).bitmap

        // Nothing is painted outside the padded content, so the canvas keeps its
        // transparent pixels instead of a baked-in background.
        assertEquals(0, out.getPixel(2, 2))
        assertEquals(0, out.getPixel(80, 4))
        assertTrue(out.near(80, 110, Color.RED))
    }

    @Test
    @Config(sdk = [28])
    fun `save writes a decodable jpeg to disk`() = runTest {
        val cfg = LayoutConfig(sizeMode = SizeMode.NATIVE, format = OutFormat.JPEG)
        val exporter = StitchExporter(context, BitmapLoader(context.contentResolver))

        val rendered = exporter.render(
            project(
                "a" to registerShot("a", Color.RED),
                cfg = cfg,
            ),
        )
        val uri = exporter.save(
            project("a" to registerShot("a2", Color.RED), cfg = cfg),
            rendered,
        )

        assertNotNull("save returned a uri", uri)
        val file = requireNotNull(uri).path?.let { java.io.File(it) }
        assertNotNull("save returned a file uri", file)
        assertTrue("file exists", requireNotNull(file).exists())
        val decoded = BitmapFactory.decodeFile(requireNotNull(file).absolutePath)
        assertNotNull("file decodes", decoded)
        assertEquals(160, decoded.width)
        assertEquals(220, decoded.height)
    }
}

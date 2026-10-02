package com.kouzenneo.tsugi.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.kouzenneo.tsugi.ui.theme.TsugiTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real editor screen and captures what the user would see.
 *
 * The view model's own coroutines cannot be driven from this environment, so this
 * covers the surface itself — theme, layout, chrome — while [StitchRenderTest] covers
 * what those pixels are made of.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenRenderTest {

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 1920)
    }

    private fun screenshot(view: View, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    @Test
    fun `the empty editor renders its chrome`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext())
        val view = ComposeView(activity)
        view.setContent { TsugiTheme { EditorScreen(vm) } }
        activity.setContentView(view)

        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            layout(view)
        }
        val screen = screenshot(activity.window.decorView, "editor-empty")

        val pixels = IntArray(screen.width * screen.height)
        screen.getPixels(pixels, 0, screen.width, 0, 0, screen.width, screen.height)
        val distinct = pixels.toHashSet()
        assertTrue("screen is not a flat fill", distinct.size > 8)
        val ink = pixels.count { luminance(it) < 80 }
        val surface = pixels.count { luminance(it) > 240 }
        assertTrue("chrome draws dark text over a light surface (ink=$ink)", ink > 500)
        assertTrue("the surface is painted (surface=$surface)", surface > pixels.size / 2)
    }

    private fun luminance(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}

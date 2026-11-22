package io.github.bydavy.liene.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePrepTest {
    @Test
    fun fitAddsWhiteBarsForASquarePhoto() {
        val source = solidBitmap(600, 600, Color.RED)
        val jpeg = prepareJpeg(
            source = source,
            rotation = 0,
            fit = Fit.Contain,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
            width = 600,
            height = 900,
        )
        assertTrue(jpeg.size <= MAX_JPEG_BYTES)

        val output = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(600, output.width)
        assertEquals(900, output.height)
        assertNearWhite(output.getPixel(300, 20))
        assertNearRed(output.getPixel(300, 450))
    }

    @Test
    fun fillCoversTheWholePaper() {
        val source = solidBitmap(600, 600, Color.RED)
        val jpeg = prepareJpeg(
            source = source,
            rotation = 0,
            fit = Fit.Cover,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
            width = 600,
            height = 900,
        )
        val output = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNearRed(output.getPixel(300, 20))
        assertNearRed(output.getPixel(300, 450))
        assertNearRed(output.getPixel(300, 880))
    }

    @Test
    fun rotationChangesTheFraming() {
        val source = solidBitmap(900, 600, Color.RED)
        val jpeg = prepareJpeg(
            source = source,
            rotation = 90,
            fit = Fit.Contain,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
            width = 600,
            height = 900,
        )
        val output = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        // The 900×600 source becomes 600×900 when rotated, filling the paper.
        assertNearRed(output.getPixel(300, 20))
        assertNearRed(output.getPixel(300, 880))
    }

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun assertNearWhite(color: Int) {
        assertTrue("expected white, got ${color.toUInt().toString(16)}",
            Color.red(color) > 240 && Color.green(color) > 240 && Color.blue(color) > 240)
    }

    private fun assertNearRed(color: Int) {
        assertTrue("expected red, got ${color.toUInt().toString(16)}",
            Color.red(color) > 180 && Color.green(color) < 70 && Color.blue(color) < 70)
    }
}

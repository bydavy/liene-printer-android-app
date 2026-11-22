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
class CollageTest {
    @Test
    fun layoutsMatchThePhotoCount() {
        for (count in 2..4) {
            val layouts = collageLayoutsFor(count)
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { assertEquals(count, it.cells.size) }
        }
    }

    @Test
    fun rendersThreeColumnsWithTheRightColors() {
        val images = listOf(
            solid(Color.RED),
            solid(Color.GREEN),
            solid(Color.BLUE),
        )
        val layout = collageLayoutsFor(3).first { it.id == "h3" }
        val output = renderCollage(images, layout, 600, 900)
        try {
            layout.cells.forEachIndexed { index, cell ->
                val x = ((cell.left + cell.right) / 2f * 600f).toInt()
                val y = ((cell.top + cell.bottom) / 2f * 900f).toInt()
                val color = output.getPixel(x, y)
                when (index) {
                    0 -> assertTrue(Color.red(color) > 180 && Color.green(color) < 70)
                    1 -> assertTrue(Color.green(color) > 180 && Color.red(color) < 70)
                    2 -> assertTrue(Color.blue(color) > 180 && Color.red(color) < 70)
                }
            }
        } finally {
            output.recycle()
        }
    }

    @Test
    fun jpegStaysWithinTheTransferBudget() {
        val images = listOf(solid(Color.RED), solid(Color.GREEN), solid(Color.BLUE))
        val layout = collageLayoutsFor(3).first()
        val jpeg = renderCollageJpeg(images, layout)
        assertTrue(jpeg.size <= MAX_JPEG_BYTES)
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(PRINT_WIDTH, decoded.width)
        assertEquals(PRINT_HEIGHT, decoded.height)
        decoded.recycle()
    }

    private fun solid(color: Int): Bitmap =
        Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}

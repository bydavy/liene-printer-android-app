package io.github.bydavy.liene.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/** Matches the vendor app's ~1 MiB JPEG transfer target. */
const val MAX_JPEG_BYTES = 1023 * 1024

/**
 * Render the edited photo into the exact 2:3 JPEG the printer receives.
 *
 * The math is shared with the on-screen preview so Fit/white borders and
 * Fill/cropping match what the user sees.
 */
fun prepareJpeg(
    source: Bitmap,
    rotation: Int,
    fit: Fit,
    zoom: Float,
    panX: Float,
    panY: Float,
    width: Int,
    height: Int,
): ByteArray {
    require(width > 0 && height > 0) { "canvas dimensions must be non-zero" }
    val rotated = ImageStore.rotate(source, rotation)
    try {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val rect = computeDrawRect(
                sourceWidth = rotated.width,
                sourceHeight = rotated.height,
                canvasWidth = width.toFloat(),
                canvasHeight = height.toFloat(),
                fit = fit,
                zoom = zoom,
                panX = panX,
                panY = panY,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(
                rotated,
                null,
                RectF(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height),
                paint,
            )
            return encodeJpegWithinBudget(output)
        } finally {
            output.recycle()
        }
    } finally {
        if (rotated !== source) rotated.recycle()
    }
}

private fun encodeJpegWithinBudget(bitmap: Bitmap): ByteArray {
    var quality = 92
    while (true) {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (output.size() <= MAX_JPEG_BYTES || quality <= 40) {
            return output.toByteArray()
        }
        quality -= 4
    }
}

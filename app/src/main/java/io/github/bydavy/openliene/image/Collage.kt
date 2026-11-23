package io.github.bydavy.openliene.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

data class CollageCell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class CollageLayout(
    val id: String,
    val name: String,
    val cells: List<CollageCell>,
)

data class PhotoFrame(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Int = 0,
)

data class CollagePhoto(
    val bitmap: Bitmap,
    val frame: PhotoFrame = PhotoFrame(),
)

private const val MARGIN = 0.035f
private const val GAP = 0.018f

fun collageLayoutsFor(count: Int, borders: Boolean = true): List<CollageLayout> {
    val margin = if (borders) MARGIN else 0f
    val gap = if (borders) GAP else 0f
    return when (count) {
        2 -> listOf(
            gridLayout("v2", "Vertical", listOf(1f, 1f), listOf(1f), margin, gap),
            gridLayout("h2", "Horizontal", listOf(1f), listOf(1f, 1f), margin, gap),
            gridLayout("top2", "Top focus", listOf(0.62f, 0.38f), listOf(1f), margin, gap),
            gridLayout("left2", "Left focus", listOf(1f), listOf(0.62f, 0.38f), margin, gap),
        )

        3 -> listOf(
            gridLayout("v3", "Vertical", listOf(1f, 1f, 1f), listOf(1f), margin, gap),
            gridLayout("h3", "Horizontal", listOf(1f), listOf(1f, 1f, 1f), margin, gap),
            heroLayout("left3", "Left hero", true, margin, gap),
            heroLayout("top3", "Top hero", false, margin, gap),
        )

        4 -> listOf(
            gridLayout("grid4", "2×2", listOf(1f, 1f), listOf(1f, 1f), margin, gap),
            gridLayout("v4", "4 down", listOf(1f, 1f, 1f, 1f), listOf(1f), margin, gap),
            gridLayout("h4", "4 across", listOf(1f), listOf(1f, 1f, 1f, 1f), margin, gap),
            heroGridLayout("left4", "Left hero", 3, margin, gap),
        )

        else -> emptyList()
    }
}

private fun gridLayout(
    id: String,
    name: String,
    rows: List<Float>,
    cols: List<Float>,
    margin: Float,
    gap: Float,
): CollageLayout = CollageLayout(id, name, grid(rows, cols, margin, gap))

private fun grid(
    rows: List<Float>,
    cols: List<Float>,
    margin: Float,
    gap: Float,
): List<CollageCell> {
    val contentW = 1f - 2f * margin - gap * (cols.size - 1)
    val contentH = 1f - 2f * margin - gap * (rows.size - 1)
    val rowSum = rows.sum()
    val colSum = cols.sum()
    val heights = rows.map { contentH * it / rowSum }
    val widths = cols.map { contentW * it / colSum }
    val cells = ArrayList<CollageCell>(rows.size * cols.size)
    var y = margin
    for (r in rows.indices) {
        var x = margin
        for (c in cols.indices) {
            cells += CollageCell(x, y, x + widths[c], y + heights[r])
            x += widths[c] + gap
        }
        y += heights[r] + gap
    }
    return cells
}

/** One large cell on the left/top, the remaining cells stacked beside it. */
private fun heroLayout(
    id: String,
    name: String,
    heroFirst: Boolean,
    margin: Float,
    gap: Float,
): CollageLayout {
    val contentW = 1f - 2f * margin
    val contentH = 1f - 2f * margin
    val heroShare = 0.62f
    return if (heroFirst) {
        val split = margin + (contentW - gap) * heroShare
        val cells = listOf(
            CollageCell(margin, margin, split, 1f - margin),
        ).toMutableList()
        val smallH = (contentH - gap) / 2f
        cells += CollageCell(split + gap, margin, 1f - margin, margin + smallH)
        cells += CollageCell(
            split + gap,
            margin + smallH + gap,
            1f - margin,
            1f - margin,
        )
        CollageLayout(id, name, cells)
    } else {
        val split = margin + (contentH - gap) * heroShare
        val cells = listOf(
            CollageCell(margin, margin, 1f - margin, split),
        ).toMutableList()
        val smallW = (contentW - gap) / 2f
        cells += CollageCell(margin, split + gap, margin + smallW, 1f - margin)
        cells += CollageCell(
            margin + smallW + gap,
            split + gap,
            1f - margin,
            1f - margin,
        )
        CollageLayout(id, name, cells)
    }
}

/** A left-hand hero plus a stack of [heroRows] photos on the right. */
private fun heroGridLayout(
    id: String,
    name: String,
    heroRows: Int,
    margin: Float,
    gap: Float,
): CollageLayout {
    val contentW = 1f - 2f * margin
    val contentH = 1f - 2f * margin
    val split = margin + (contentW - gap) / 2f
    val cells = mutableListOf(
        CollageCell(margin, margin, split, 1f - margin),
    )
    val smallH = (contentH - gap * (heroRows - 1)) / heroRows
    for (i in 0 until heroRows) {
        val top = margin + i * (smallH + gap)
        cells += CollageCell(split + gap, top, 1f - margin, top + smallH)
    }
    return CollageLayout(id, name, cells)
}

fun renderCollage(
    photos: List<CollagePhoto>,
    layout: CollageLayout,
    width: Int,
    height: Int,
): Bitmap {
    require(photos.size == layout.cells.size) {
        "layout ${layout.id} needs ${layout.cells.size} photos, got ${photos.size}"
    }
    require(width > 0 && height > 0)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    layout.cells.forEachIndexed { index, cell ->
        val photo = photos[index]
        val dst = RectF(
            cell.left * width,
            cell.top * height,
            cell.right * width,
            cell.bottom * height,
        )
        val rotated = ImageStore.rotate(photo.bitmap, photo.frame.rotation)
        try {
            val src = sourceCrop(
                rotated.width,
                rotated.height,
                dst.width(),
                dst.height(),
                photo.frame,
            )
            canvas.drawBitmap(rotated, src, dst, paint)
        } finally {
            if (rotated !== photo.bitmap) rotated.recycle()
        }
    }
    return output
}

fun renderCollageJpeg(
    photos: List<CollagePhoto>,
    layout: CollageLayout,
    width: Int = PRINT_WIDTH,
    height: Int = PRINT_HEIGHT,
): ByteArray {
    val collage = renderCollage(photos, layout, width, height)
    return try {
        encodeJpeg(collage)
    } finally {
        collage.recycle()
    }
}

fun applyPhotoGesture(
    frame: PhotoFrame,
    zoomChange: Float,
    panPxX: Float,
    panPxY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    sourceWidth: Int,
    sourceHeight: Int,
): PhotoFrame {
    val zoom = (frame.zoom * zoomChange).coerceIn(1f, 8f)
    val rect = computeDrawRect(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        canvasWidth = viewportWidth,
        canvasHeight = viewportHeight,
        fit = Fit.Cover,
        zoom = zoom,
    )
    val slackX = (rect.width - viewportWidth).coerceAtLeast(0f)
    val slackY = (rect.height - viewportHeight).coerceAtLeast(0f)
    return frame.copy(
        zoom = zoom,
        panX = if (slackX > 0f) {
            (frame.panX + panPxX / (slackX / 2f)).coerceIn(-1f, 1f)
        } else {
            0f
        },
        panY = if (slackY > 0f) {
            (frame.panY + panPxY / (slackY / 2f)).coerceIn(-1f, 1f)
        } else {
            0f
        },
    )
}

private fun sourceCrop(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Float,
    targetHeight: Float,
    frame: PhotoFrame,
): Rect {
    val baseScale = maxOf(
        targetWidth / sourceWidth,
        targetHeight / sourceHeight,
    )
    val scale = baseScale * frame.zoom.coerceIn(1f, 8f)
    val drawWidth = sourceWidth * scale
    val drawHeight = sourceHeight * scale
    val slackX = (drawWidth - targetWidth).coerceAtLeast(0f)
    val slackY = (drawHeight - targetHeight).coerceAtLeast(0f)
    val left = (targetWidth - drawWidth) / 2f +
        frame.panX.coerceIn(-1f, 1f) * slackX / 2f
    val top = (targetHeight - drawHeight) / 2f +
        frame.panY.coerceIn(-1f, 1f) * slackY / 2f
    val cropWidth = (targetWidth / scale).coerceIn(1f, sourceWidth.toFloat())
    val cropHeight = (targetHeight / scale).coerceIn(1f, sourceHeight.toFloat())
    val x = (-left / scale).coerceIn(0f, sourceWidth - cropWidth)
    val y = (-top / scale).coerceIn(0f, sourceHeight - cropHeight)
    return Rect(
        x.roundToInt(),
        y.roundToInt(),
        (x + cropWidth).roundToInt().coerceAtMost(sourceWidth),
        (y + cropHeight).roundToInt().coerceAtMost(sourceHeight),
    )
}

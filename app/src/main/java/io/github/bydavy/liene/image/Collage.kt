package io.github.bydavy.liene.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

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

private const val MARGIN = 0.035f
private const val GAP = 0.018f

fun collageLayoutsFor(count: Int): List<CollageLayout> = when (count) {
    2 -> listOf(
        gridLayout("v2", "Vertical", rows = listOf(1f, 1f), cols = listOf(1f)),
        gridLayout("h2", "Horizontal", rows = listOf(1f), cols = listOf(1f, 1f)),
        gridLayout("top2", "Top focus", rows = listOf(0.62f, 0.38f), cols = listOf(1f)),
        gridLayout("left2", "Left focus", rows = listOf(1f), cols = listOf(0.62f, 0.38f)),
    )

    3 -> listOf(
        gridLayout("v3", "Vertical", rows = listOf(1f, 1f, 1f), cols = listOf(1f)),
        gridLayout("h3", "Horizontal", rows = listOf(1f), cols = listOf(1f, 1f, 1f)),
        heroLayout("left3", "Left hero", heroFirst = true),
        heroLayout("top3", "Top hero", heroFirst = false),
    )

    4 -> listOf(
        gridLayout("grid4", "2×2", rows = listOf(1f, 1f), cols = listOf(1f, 1f)),
        gridLayout("v4", "4 down", rows = listOf(1f, 1f, 1f, 1f), cols = listOf(1f)),
        gridLayout("h4", "4 across", rows = listOf(1f), cols = listOf(1f, 1f, 1f, 1f)),
        heroGridLayout("left4", "Left hero", heroRows = 3),
    )

    else -> emptyList()
}

private fun gridLayout(
    id: String,
    name: String,
    rows: List<Float>,
    cols: List<Float>,
): CollageLayout = CollageLayout(id, name, grid(rows, cols))

private fun grid(rows: List<Float>, cols: List<Float>): List<CollageCell> {
    val contentW = 1f - 2f * MARGIN - GAP * (cols.size - 1)
    val contentH = 1f - 2f * MARGIN - GAP * (rows.size - 1)
    val rowSum = rows.sum()
    val colSum = cols.sum()
    val heights = rows.map { contentH * it / rowSum }
    val widths = cols.map { contentW * it / colSum }
    val cells = ArrayList<CollageCell>(rows.size * cols.size)
    var y = MARGIN
    for (r in rows.indices) {
        var x = MARGIN
        for (c in cols.indices) {
            cells += CollageCell(x, y, x + widths[c], y + heights[r])
            x += widths[c] + GAP
        }
        y += heights[r] + GAP
    }
    return cells
}

/** One large cell on the left/top, the remaining cells stacked beside it. */
private fun heroLayout(id: String, name: String, heroFirst: Boolean): CollageLayout {
    val contentW = 1f - 2f * MARGIN
    val contentH = 1f - 2f * MARGIN
    val heroShare = 0.62f
    return if (heroFirst) {
        val split = MARGIN + (contentW - GAP) * heroShare
        val cells = listOf(
            CollageCell(MARGIN, MARGIN, split, 1f - MARGIN),
        ).toMutableList()
        val smallH = (contentH - GAP) / 2f
        cells += CollageCell(split + GAP, MARGIN, 1f - MARGIN, MARGIN + smallH)
        cells += CollageCell(
            split + GAP,
            MARGIN + smallH + GAP,
            1f - MARGIN,
            1f - MARGIN,
        )
        CollageLayout(id, name, cells)
    } else {
        val split = MARGIN + (contentH - GAP) * heroShare
        val cells = listOf(
            CollageCell(MARGIN, MARGIN, 1f - MARGIN, split),
        ).toMutableList()
        val smallW = (contentW - GAP) / 2f
        cells += CollageCell(MARGIN, split + GAP, MARGIN + smallW, 1f - MARGIN)
        cells += CollageCell(
            MARGIN + smallW + GAP,
            split + GAP,
            1f - MARGIN,
            1f - MARGIN,
        )
        CollageLayout(id, name, cells)
    }
}

/** A left-hand hero plus a stack of [heroRows] photos on the right. */
private fun heroGridLayout(id: String, name: String, heroRows: Int): CollageLayout {
    val contentW = 1f - 2f * MARGIN
    val contentH = 1f - 2f * MARGIN
    val split = MARGIN + (contentW - GAP) / 2f
    val cells = mutableListOf(
        CollageCell(MARGIN, MARGIN, split, 1f - MARGIN),
    )
    val smallH = (contentH - GAP * (heroRows - 1)) / heroRows
    for (i in 0 until heroRows) {
        val top = MARGIN + i * (smallH + GAP)
        cells += CollageCell(split + GAP, top, 1f - MARGIN, top + smallH)
    }
    return CollageLayout(id, name, cells)
}

fun renderCollage(
    images: List<Bitmap>,
    layout: CollageLayout,
    width: Int,
    height: Int,
): Bitmap {
    require(images.size == layout.cells.size) {
        "layout ${layout.id} needs ${layout.cells.size} photos, got ${images.size}"
    }
    require(width > 0 && height > 0)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    layout.cells.forEachIndexed { index, cell ->
        val image = images[index]
        val dst = RectF(
            cell.left * width,
            cell.top * height,
            cell.right * width,
            cell.bottom * height,
        )
        val src = centerCrop(image.width, image.height, dst.width(), dst.height())
        canvas.drawBitmap(image, src, dst, paint)
    }
    return output
}

fun renderCollageJpeg(
    images: List<Bitmap>,
    layout: CollageLayout,
    width: Int = PRINT_WIDTH,
    height: Int = PRINT_HEIGHT,
): ByteArray {
    val collage = renderCollage(images, layout, width, height)
    return try {
        encodeJpeg(collage)
    } finally {
        collage.recycle()
    }
}

private fun centerCrop(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Float,
    targetHeight: Float,
): Rect {
    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    val targetAspect = targetWidth / targetHeight
    return if (sourceAspect > targetAspect) {
        val cropWidth = (sourceHeight * targetAspect).toInt().coerceAtLeast(1)
        val left = (sourceWidth - cropWidth) / 2
        Rect(left, 0, left + cropWidth, sourceHeight)
    } else {
        val cropHeight = (sourceWidth / targetAspect).toInt().coerceAtLeast(1)
        val top = (sourceHeight - cropHeight) / 2
        Rect(0, top, sourceWidth, top + cropHeight)
    }
}

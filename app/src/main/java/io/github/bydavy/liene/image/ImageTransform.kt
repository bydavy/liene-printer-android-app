package io.github.bydavy.liene.image

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

enum class Fit(val label: String) {
    /** Whole photo, padded with white. */
    Contain("Fit"),

    /** Fill the 2:3 print, cropping the overflow. */
    Cover("Fill"),
}

enum class Quality(val width: Int, val height: Int, val label: String) {
    Native(600, 900, "600×900"),
    High(1200, 1800, "1200×1800"),
}

data class EditorState(
    val source: Bitmap? = null,
    val preview: Bitmap? = null,
    val fileName: String? = null,
    val rotation: Int = 0,
    val fit: Fit = Fit.Contain,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val copies: Int = 1,
    val quality: Quality = Quality.High,
    val loading: Boolean = false,
    val preparing: Boolean = false,
    val error: String? = null,
) {
    fun rotated(clockwise: Boolean = true): EditorState = copy(
        rotation = if (clockwise) {
            (rotation + 90) % 360
        } else {
            (rotation + 270) % 360
        },
        zoom = 1f,
        panX = 0f,
        panY = 0f,
    )

    fun resetFraming(): EditorState = copy(
        rotation = 0,
        zoom = 1f,
        panX = 0f,
        panY = 0f,
    )
}

data class DrawRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Compute where the rotated source image lands on the 2:3 canvas.
 *
 * `zoom` and `pan` apply only to [Fit.Cover]. Pan values are normalized to
 * the available slack: -1 is the left/top edge and +1 the right/bottom edge.
 */
fun computeDrawRect(
    sourceWidth: Int,
    sourceHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    fit: Fit,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
): DrawRect {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(canvasWidth > 0f && canvasHeight > 0f)

    val baseScale = if (fit == Fit.Cover) {
        max(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
    } else {
        min(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
    }
    val scale = baseScale * if (fit == Fit.Cover) zoom.coerceIn(1f, 8f) else 1f
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    val slackX = (width - canvasWidth).coerceAtLeast(0f)
    val slackY = (height - canvasHeight).coerceAtLeast(0f)
    val offsetX = if (fit == Fit.Cover) panX.coerceIn(-1f, 1f) * slackX / 2f else 0f
    val offsetY = if (fit == Fit.Cover) panY.coerceIn(-1f, 1f) * slackY / 2f else 0f
    return DrawRect(
        left = (canvasWidth - width) / 2f + offsetX,
        top = (canvasHeight - height) / 2f + offsetY,
        width = width,
        height = height,
    )
}

/** Apply a pinch/pan gesture while keeping the image covered. */
fun applyGesture(
    state: EditorState,
    zoomChange: Float,
    panPxX: Float,
    panPxY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    sourceWidth: Int,
    sourceHeight: Int,
): EditorState {
    if (state.fit != Fit.Cover) return state
    val newZoom = (state.zoom * zoomChange).coerceIn(1f, 8f)
    val baseRect = computeDrawRect(
        sourceWidth,
        sourceHeight,
        viewportWidth,
        viewportHeight,
        Fit.Cover,
        newZoom,
    )
    val slackX = (baseRect.width - viewportWidth).coerceAtLeast(0f)
    val slackY = (baseRect.height - viewportHeight).coerceAtLeast(0f)
    val newPanX = if (slackX > 0f) {
        (state.panX + panPxX / (slackX / 2f)).coerceIn(-1f, 1f)
    } else {
        0f
    }
    val newPanY = if (slackY > 0f) {
        (state.panY + panPxY / (slackY / 2f)).coerceIn(-1f, 1f)
    } else {
        0f
    }
    return state.copy(zoom = newZoom, panX = newPanX, panY = newPanY)
}

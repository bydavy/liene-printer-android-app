package io.github.bydavy.liene.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageTransformTest {
    @Test
    fun containLetterboxesWithWhiteBars() {
        val rect = computeDrawRect(
            sourceWidth = 1000,
            sourceHeight = 1000,
            canvasWidth = 600f,
            canvasHeight = 900f,
            fit = Fit.Contain,
        )
        assertEquals(600f, rect.width, 0.01f)
        assertEquals(600f, rect.height, 0.01f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(150f, rect.top, 0.01f)
    }

    @Test
    fun coverCropsToFillTheCanvas() {
        val rect = computeDrawRect(
            sourceWidth = 1000,
            sourceHeight = 1000,
            canvasWidth = 600f,
            canvasHeight = 900f,
            fit = Fit.Cover,
        )
        assertEquals(900f, rect.width, 0.01f)
        assertEquals(900f, rect.height, 0.01f)
        assertEquals(-150f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
    }

    @Test
    fun panIsClampedToTheAvailableSlack() {
        val right = computeDrawRect(
            sourceWidth = 1000,
            sourceHeight = 1000,
            canvasWidth = 600f,
            canvasHeight = 900f,
            fit = Fit.Cover,
            panX = 1f,
        )
        val left = computeDrawRect(
            sourceWidth = 1000,
            sourceHeight = 1000,
            canvasWidth = 600f,
            canvasHeight = 900f,
            fit = Fit.Cover,
            panX = -1f,
        )
        assertEquals(0f, right.left, 0.01f)
        assertEquals(-300f, left.left, 0.01f)
    }

    @Test
    fun zoomIncreasesTheDrawSize() {
        val rect = computeDrawRect(
            sourceWidth = 1000,
            sourceHeight = 1000,
            canvasWidth = 600f,
            canvasHeight = 900f,
            fit = Fit.Cover,
            zoom = 2f,
        )
        assertEquals(1800f, rect.width, 0.01f)
        assertEquals(1800f, rect.height, 0.01f)
        assertEquals(-600f, rect.left, 0.01f)
    }

    @Test
    fun gestureChangesZoomAndPan() {
        val state = EditorState(fit = Fit.Cover)
        val next = applyGesture(
            state = state,
            zoomChange = 2f,
            panPxX = 100f,
            panPxY = 0f,
            viewportWidth = 600f,
            viewportHeight = 900f,
            sourceWidth = 1000,
            sourceHeight = 1000,
        )
        assertEquals(2f, next.zoom, 0.01f)
        // At 2× the width is 1800px, so each side has 600px of slack; a
        // 100px drag is 1/6 of the half-slack.
        assertEquals(1f / 6f, next.panX, 0.01f)
    }

    @Test
    fun rotateResetsZoomAndPan() {
        val rotated = EditorState(
            fit = Fit.Cover,
            zoom = 4f,
            panX = 0.5f,
            panY = -0.5f,
            rotation = 90,
        ).rotated()
        assertEquals(180, rotated.rotation)
        assertEquals(1f, rotated.zoom, 0.01f)
        assertEquals(0f, rotated.panX, 0.01f)
        assertEquals(0f, rotated.panY, 0.01f)
    }
}

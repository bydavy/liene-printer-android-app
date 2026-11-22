package io.github.bydavy.liene.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bydavy.liene.CollageState
import io.github.bydavy.liene.image.CollageLayout
import io.github.bydavy.liene.image.CollagePhoto
import io.github.bydavy.liene.image.collageLayoutsFor
import io.github.bydavy.liene.image.renderCollage
import io.github.bydavy.liene.printer.ConnectionState
import io.github.bydavy.liene.printer.PrinterStatus
import io.github.bydavy.liene.printer.PrinterUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi + Robolectric screenshots and interactions for the collage UI.
 * Record new goldens with `./gradlew recordRoborazziDebug`; verify them with
 * `./gradlew verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-440dpi")
class CollageScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collageEditorMatchesGolden() {
        setEditor(collage())
        composeRule.onNodeWithTag("layout-h3").assertExists()
        composeRule.onRoot().captureRoboImage("collage/editor.png")
    }

    @Test
    fun layoutAndBordersClicksReachCallbacks() {
        var selected: String? = null
        var borders: Boolean? = null
        setEditor(
            collage(),
            onLayout = { selected = it.id },
            onBorders = { borders = it },
        )

        composeRule.onNodeWithTag("layout-v3").performClick()
        assertEquals("v3", selected)

        composeRule.onNodeWithTag("collage-borders").performClick()
        assertEquals(false, borders)
    }

    @Test
    fun tappingAPhotoOpensItsFrameEditor() {
        var edited: Int? = null
        setEditor(collage(), onEditPhoto = { edited = it })

        composeRule.onNodeWithTag("collage-preview").performTouchInput {
            click(Offset(width * 0.5f, height * 0.5f))
        }
        assertEquals(1, edited)
    }

    @Test
    fun randomizeAndPrintButtonsReachCallbacks() {
        var randomized = false
        var printed = false
        setEditor(
            collage(),
            onRandomize = { randomized = true },
            onPrint = { printed = true },
        )

        composeRule.onNodeWithText("Randomize photos").performClick()
        assertTrue(randomized)
        composeRule.onNodeWithText("Print").performClick()
        assertTrue(printed)
    }

    @Test
    fun frameEditorMatchesGoldenAndRotates() {
        var rotated = false
        val state = collage()
        composeRule.setContent {
            LieneTheme {
                Scaffold(
                    bottomBar = {
                        CollagePhotoFrameBar(
                            onReplace = {},
                            onRotate = { rotated = true },
                            onReset = {},
                            onDone = {},
                        )
                    },
                ) { padding ->
                    CollagePhotoFrameContent(
                        collage = state,
                        index = 0,
                        onGesture = { _, _, _, _, _ -> },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("collage-frame-viewport").assertExists()
        composeRule.onRoot().captureRoboImage("collage/frame-editor.png")
        composeRule.onNodeWithText("Rotate 90°").performClick()
        assertTrue(rotated)
    }

    @Test
    fun printPreviewMatchesGolden() {
        setPrintPreview(collage().copy(copies = 2))
        composeRule.onRoot().captureRoboImage("collage/print-preview.png")
    }

    @Test
    fun copyButtonsReachCallback() {
        var copies: Int? = null
        setPrintPreview(collage().copy(copies = 2), onCopies = { copies = it })

        composeRule.onNodeWithText("+").performClick()
        assertEquals(3, copies)
        composeRule.onNodeWithText("−").performClick()
        assertEquals(1, copies)
    }

    private fun setPrintPreview(
        state: CollageState,
        onCopies: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            LieneTheme {
                Scaffold(
                    bottomBar = {
                        CollagePreviewBar(state, connectedPrinter(), onPrint = {})
                    },
                ) { padding ->
                    CollagePrintPreviewContent(
                        collage = state,
                        printer = connectedPrinter(),
                        error = null,
                        onCopies = onCopies,
                        onConnect = {},
                        onDisconnect = {},
                        onRefresh = {},
                        onResume = {},
                        onSettings = {},
                        onDismissError = {},
                        onCancelJob = {},
                        onDismissJob = {},
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private fun setEditor(
        state: CollageState,
        onLayout: (CollageLayout) -> Unit = {},
        onBorders: (Boolean) -> Unit = {},
        onEditPhoto: (Int) -> Unit = {},
        onRandomize: () -> Unit = {},
        onPrint: () -> Unit = {},
    ) {
        composeRule.setContent {
            LieneTheme {
                Scaffold(
                    bottomBar = { CollageEditorBar(state, onPrint) },
                ) { padding ->
                    CollageEditorContent(
                        collage = state,
                        error = null,
                        connected = false,
                        onDismissError = {},
                        onResume = {},
                        onLayout = onLayout,
                        onBorders = onBorders,
                        onTransformPhoto = { _, _, _, _, _, _ -> },
                        onEditPhoto = onEditPhoto,
                        onRandomize = onRandomize,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private fun collage(): CollageState {
        val photos = (0..2).map { CollagePhoto(photo(it)) }
        val layouts = collageLayoutsFor(3)
        val layout = layouts.first { it.id == "h3" }
        return CollageState(
            photos = photos,
            preview = renderCollage(photos, layout, PRINT_PREVIEW_WIDTH, PRINT_PREVIEW_HEIGHT),
            layouts = layouts,
            layout = layout,
        )
    }

    private fun photo(index: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            600f,
            900f,
            intArrayOf(
                Color.rgb(232, 62, 52),
                Color.rgb(250, 170, 48),
                Color.rgb(38, 166, 92),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 600f, 900f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        canvas.drawCircle(300f, 380f, 110f + index * 30f, paint)
        paint.color = Color.argb(190, 22, 30, 60)
        canvas.drawRect(0f, 690f + index * 40f, 600f, 750f + index * 40f, paint)
        return bitmap
    }

    private fun connectedPrinter() = PrinterUiState(
        connection = ConnectionState(
            connected = true,
            name = "Liene N200 Pro",
            address = "AA:BB:CC:DD:EE:FF",
        ),
        status = PrinterStatus(state = 20, subState = 0, alerts = emptyList(), battery = 4),
    )

    private companion object {
        const val PRINT_PREVIEW_WIDTH = 600
        const val PRINT_PREVIEW_HEIGHT = 900
    }
}

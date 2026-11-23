package io.github.bydavy.openliene.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bydavy.openliene.image.EditorState
import io.github.bydavy.openliene.image.Fit
import io.github.bydavy.openliene.printer.ConnectionState
import io.github.bydavy.openliene.printer.DeviceInfo
import io.github.bydavy.openliene.printer.JobProgress
import io.github.bydavy.openliene.printer.PrinterStatus
import io.github.bydavy.openliene.printer.PrinterUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi + Robolectric screenshots and interactions for the single-photo
 * editor, print confirmation, printer/job cards, and diagnostics.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-440dpi")
class EditorScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyEditorMatchesGolden() {
        setPhotoEditor(EditorState(), PrinterUiState(), canPrint = false)
        composeRule.onNodeWithText("Share a photo").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("editor/empty.png")
    }

    @Test
    fun loadedEditorMatchesGolden() {
        setPhotoEditor(loadedEditor(), connectedPrinter(), canPrint = true)
        composeRule.onNodeWithText("Framing").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("editor/loaded.png")
    }

    @Test
    fun editorControlsReachCallbacks() {
        var chosen = false
        var collage = false
        var rotated = false
        var reset = false
        var fit: Fit? = null
        setPhotoEditor(
            loadedEditor(),
            connectedPrinter(),
            canPrint = true,
            onChoose = { chosen = true },
            onCollage = { collage = true },
            onRotate = { rotated = true },
            onReset = { reset = true },
            onFit = { fit = it },
        )

        composeRule.onNodeWithText("Choose").performClick()
        assertTrue(chosen)
        composeRule.onNodeWithText("Collage").performClick()
        assertTrue(collage)
        composeRule.onNodeWithText("Rotate 90°").performClick()
        assertTrue(rotated)
        composeRule.onNodeWithText("Reset").performClick()
        assertTrue(reset)
        composeRule.onNodeWithText("Fill").performClick()
        assertEquals(Fit.Cover, fit)
        composeRule.onNodeWithText("Fit").performClick()
        assertEquals(Fit.Contain, fit)
    }

    @Test
    fun printSettingsCopiesReachCallbacks() {
        var copies: Int? = null
        setContent {
            PrintSettingsCard(loadedEditor(copies = 2), onCopies = { copies = it })
        }

        composeRule.onNodeWithTag("copies-plus").performClick()
        assertEquals(3, copies)
        composeRule.onNodeWithTag("copies-minus").performClick()
        assertEquals(1, copies)
    }

    @Test
    fun printBarExplainsWhyPrintingIsBlocked() {
        setPhotoEditor(loadedEditor(), PrinterUiState(), canPrint = false)
        composeRule.onNodeWithText("Connect the printer to print").assertIsDisplayed()
        composeRule.onNodeWithTag("print-action").assertIsNotEnabled()
    }

    @Test
    fun printBarIsReadyWithAConnectedPrinter() {
        setPhotoEditor(loadedEditor(), connectedPrinter(), canPrint = true)
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithTag("print-action").assertIsEnabled()
    }

    @Test
    fun printConfirmationMatchesGolden() {
        val editor = loadedEditor(copies = 2)
        setContent {
            PrintConfirmationDialog(editor, connectedPrinter(), onDismiss = {}, onConfirm = {})
        }

        composeRule.onNode(isDialog()).captureRoboImage("editor/print-confirmation.png")
        composeRule.onNodeWithText("Print this photo?").assertIsDisplayed()
        composeRule.onNodeWithText("Fit (white borders)").assertIsDisplayed()
    }

    @Test
    fun confirmingThePrintCallsOnConfirm() {
        var confirmed = false
        setContent {
            PrintConfirmationDialog(
                loadedEditor(),
                connectedPrinter(),
                onDismiss = {},
                onConfirm = { confirmed = true },
            )
        }
        composeRule.onNodeWithTag("confirm-print").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun cancelingTheConfirmationCallsOnDismiss() {
        var dismissed = false
        setContent {
            PrintConfirmationDialog(
                loadedEditor(),
                connectedPrinter(),
                onDismiss = { dismissed = true },
                onConfirm = {},
            )
        }
        composeRule.onNodeWithTag("cancel-print").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun printerAlertMatchesGoldenAndActionsWork() {
        var dismissed = false
        var resumed = false
        val printer = connectedPrinter(
            battery = 1,
            alerts = listOf(5402),
        )
        setContent {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(
                    message = "Paper empty — load a 2×3 cartridge",
                    showResume = true,
                    onDismiss = { dismissed = true },
                    onResume = { resumed = true },
                )
                PrinterCard(
                    printer = printer,
                    onConnect = {},
                    onDisconnect = {},
                    onRefresh = {},
                    onResume = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("editor/printer-alert.png")
        composeRule.onNodeWithText("Battery low").assertIsDisplayed()
        composeRule.onNodeWithTag("error-dismiss").performClick()
        assertTrue(dismissed)
        composeRule.onNodeWithTag("error-resume").performClick()
        assertTrue(resumed)
    }

    @Test
    fun jobProgressMatchesGoldenAndCanBeCancelled() {
        var cancelled = false
        val printer = connectedPrinter(
            busy = true,
            job = JobProgress(
                jobId = 42,
                totalFrames = 100,
                state = 3,
                transferSize = 512,
                fileSize = 1024,
                page = 1,
            ),
        )
        setContent {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JobCard(printer, onCancel = { cancelled = true }, onDismiss = {})
                DiagnosticsCard(log = listOf("Connected to Liene N200 Pro", "Battery full"))
            }
        }

        composeRule.onRoot().captureRoboImage("editor/job-progress.png")
        composeRule.onNodeWithText("Printing · page 1").assertIsDisplayed()
        composeRule.onNodeWithTag("job-cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun finishedJobCanBeDismissed() {
        var dismissed = false
        val printer = connectedPrinter(
            job = JobProgress(
                jobId = 43,
                totalFrames = 100,
                state = 9,
                transferSize = 1024,
                fileSize = 1024,
                page = 1,
                printTimeMs = 12_300,
            ),
        )
        setContent {
            JobCard(printer, onCancel = {}, onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithText("Finished in 12.3 s").assertIsDisplayed()
        composeRule.onNodeWithTag("job-dismiss").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun diagnosticsTogglesTheLog() {
        setContent {
            DiagnosticsCard(log = listOf("Connected to Liene N200 Pro", "Battery full"))
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Battery full").assertIsDisplayed()
        composeRule.onNodeWithText("Hide").performClick()
        composeRule.onNodeWithText("Battery full").assertDoesNotExist()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { LieneTheme { content() } }
    }

    private fun setPhotoEditor(
        editor: EditorState,
        printer: PrinterUiState,
        canPrint: Boolean,
        onChoose: () -> Unit = {},
        onCollage: () -> Unit = {},
        onRotate: () -> Unit = {},
        onReset: () -> Unit = {},
        onFit: (Fit) -> Unit = {},
    ) {
        composeRule.setContent {
            LieneTheme {
                Scaffold(
                    bottomBar = {
                        PrintBar(editor, printer, canPrint, onPrint = {})
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PhotoEditorCard(
                            editor = editor,
                            onChoose = onChoose,
                            onCollage = onCollage,
                            onRotate = onRotate,
                            onReset = onReset,
                            onFit = onFit,
                            onZoom = {},
                            onTransform = { _, _, _, _, _ -> },
                        )
                    }
                }
            }
        }
    }

    private fun loadedEditor(
        fit: Fit = Fit.Contain,
        copies: Int = 1,
    ): EditorState {
        val bitmap = photo()
        return EditorState(
            source = bitmap,
            preview = bitmap,
            fileName = "beach.jpg",
            fit = fit,
            copies = copies,
        )
    }

    private fun photo(): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            600f,
            900f,
            intArrayOf(Color.rgb(232, 62, 52), Color.rgb(38, 166, 92)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 600f, 900f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        canvas.drawCircle(300f, 380f, 120f, paint)
        paint.color = Color.argb(190, 22, 30, 60)
        canvas.drawRect(0f, 690f, 600f, 750f, paint)
        return bitmap
    }

    private fun connectedPrinter(
        battery: Int = 4,
        alerts: List<Int> = emptyList(),
        job: JobProgress? = null,
        busy: Boolean = false,
        uploadSent: Int = 0,
        uploadTotal: Int = 0,
    ) = PrinterUiState(
        connection = ConnectionState(
            connected = true,
            name = "Liene N200 Pro",
            address = "AA:BB:CC:DD:EE:FF",
        ),
        status = PrinterStatus(
            state = if (alerts.isEmpty()) 20 else 60,
            subState = 0,
            alerts = alerts,
            battery = battery,
        ),
        info = DeviceInfo(
            model = "Liene N200 Pro",
            serial = "SN123456",
            firmware = "1.2.3",
            bluetooth = "AA:BB:CC:DD:EE:FF",
        ),
        job = job,
        uploadSent = uploadSent,
        uploadTotal = uploadTotal,
        busy = busy,
    )
}

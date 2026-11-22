package io.github.bydavy.liene.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bydavy.liene.CollageStage
import io.github.bydavy.liene.CollageState
import io.github.bydavy.liene.LieneViewModel
import io.github.bydavy.liene.image.CollageLayout
import io.github.bydavy.liene.image.EditorState
import io.github.bydavy.liene.image.Fit
import io.github.bydavy.liene.image.ImageStore
import io.github.bydavy.liene.image.collageLayoutsFor
import io.github.bydavy.liene.image.PRINT_HEIGHT
import io.github.bydavy.liene.image.PRINT_WIDTH
import io.github.bydavy.liene.image.computeDrawRect
import io.github.bydavy.liene.printer.PrinterUiState
import io.github.bydavy.liene.printer.batteryLabel
import io.github.bydavy.liene.printer.isBatteryLow
import io.github.bydavy.liene.printer.jobStateLabel
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LieneViewModel) {
    val printer by viewModel.printer.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val collage by viewModel.collage.collectAsStateWithLifecycle()
    val collageStage by viewModel.collageStage.collectAsStateWithLifecycle()
    val collageLoading by viewModel.collageLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(viewModel::loadImage)
    }

    val pickCollage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        when {
            uris.size >= 2 -> viewModel.loadCollage(uris)
            uris.size == 1 -> viewModel.loadImage(uris.first())
            else -> viewModel.reportError("Select 2–4 photos to build a collage.")
        }
    }

    var replaceIndex by remember { mutableStateOf<Int?>(null) }
    val pickReplacement = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val index = replaceIndex
        replaceIndex = null
        if (uri != null && index != null) {
            viewModel.replaceCollagePhoto(index, uri)
        }
    }

    val requestBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.connect()
        } else {
            viewModel.reportError(
                "Bluetooth permission is required to reach the printer. " +
                    "Allow Nearby devices access in Android settings.",
            )
        }
    }

    var pendingPrint by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Even if notifications are denied, the foreground service can still
        // finish the print; the in-app progress remains visible while open.
        pendingPrint?.invoke()
        pendingPrint = null
    }

    fun connect() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.connect()
        }
    }

    fun printAction(action: () -> Unit) {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingPrint = action
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val collageActive = collage != null || collageLoading
    BackHandler(enabled = collageActive) {
        when {
            collage?.editingPhoto != null -> viewModel.closeCollagePhotoEditor()
            collageStage == CollageStage.PrintPreview -> viewModel.backToCollageEditor()
            else -> viewModel.closeCollage()
        }
    }

    val connected = printer.connection.connected
    val hasAlert = printer.status?.hasAlert == true
    val canPrint = editor.source != null &&
        connected &&
        !printer.busy &&
        !editor.preparing &&
        !hasAlert
    val error = editor.error ?: message ?: printer.error
    var confirmPrint by remember { mutableStateOf(false) }
    val activeCollage = collage
    val editingPhoto = activeCollage?.editingPhoto
    val goBack = {
        when {
            editingPhoto != null -> viewModel.closeCollagePhotoEditor()
            collageStage == CollageStage.PrintPreview -> viewModel.backToCollageEditor()
            else -> viewModel.closeCollage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (collageActive) {
                        TextButton(onClick = goBack) { Text("Back") }
                    }
                },
                title = {
                    if (collageActive) {
                        Column {
                            Text(
                                when {
                                    editingPhoto != null -> "Frame photo"
                                    collageStage == CollageStage.PrintPreview -> "Print preview"
                                    else -> "Collage"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    editingPhoto != null && activeCollage != null ->
                                        "Photo ${editingPhoto + 1} of ${activeCollage.photos.size}"
                                    activeCollage != null ->
                                        "${activeCollage.photos.size} photos · " +
                                            activeCollage.layout.name
                                    else -> "Preparing…"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column {
                            Text(
                                "Liene",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Photo printer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::refresh,
                        enabled = connected && !printer.busy,
                    ) { Text("Refresh") }
                    TextButton(onClick = { openBluetoothSettings(context) }) {
                        Text("Settings")
                    }
                },
            )
        },
        bottomBar = {
            when {
                collageLoading -> CollageLoadingBar()
                activeCollage != null && editingPhoto != null -> CollagePhotoFrameBar(
                    onReplace = {
                        replaceIndex = editingPhoto
                        pickReplacement.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onRotate = { viewModel.rotateCollagePhoto(editingPhoto) },
                    onReset = { viewModel.resetCollagePhoto(editingPhoto) },
                    onDone = viewModel::closeCollagePhotoEditor,
                )

                activeCollage != null && collageStage == CollageStage.PrintPreview ->
                    CollagePreviewBar(
                        collage = activeCollage,
                        printer = printer,
                        onPrint = {
                            printAction { viewModel.startCollagePrint() }
                        },
                    )

                activeCollage != null -> CollageEditorBar(
                    collage = activeCollage,
                    onPrint = viewModel::openCollagePreview,
                )

                else -> PrintBar(
                    editor = editor,
                    printer = printer,
                    canPrint = canPrint,
                    onPrint = { confirmPrint = true },
                )
            }
        },
    ) { innerPadding ->
        when {
            collageLoading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Preparing collage…")
                }
            }

            activeCollage != null && editingPhoto != null ->
                CollagePhotoFrameContent(
                    collage = activeCollage,
                    index = editingPhoto,
                    onGesture = { zoom, panX, panY, width, height ->
                        viewModel.applyCollagePhotoGesture(
                            editingPhoto,
                            zoom,
                            panX,
                            panY,
                            width,
                            height,
                        )
                    },
                    modifier = Modifier.padding(innerPadding),
                )

            activeCollage != null && collageStage == CollageStage.PrintPreview ->
                CollagePrintPreviewContent(
                    collage = activeCollage,
                    printer = printer,
                    error = activeCollage.error ?: message ?: printer.error,
                    onCopies = viewModel::setCollageCopies,
                    onConnect = ::connect,
                    onDisconnect = viewModel::disconnect,
                    onRefresh = viewModel::refresh,
                    onResume = viewModel::resumePrinter,
                    onSettings = { openBluetoothSettings(context) },
                    onDismissError = viewModel::dismissError,
                    onCancelJob = viewModel::cancelJob,
                    onDismissJob = viewModel::clearCompletedJob,
                    modifier = Modifier.padding(innerPadding),
                )

            activeCollage != null -> CollageEditorContent(
                collage = activeCollage,
                error = activeCollage.error ?: message ?: printer.error,
                connected = connected,
                onDismissError = viewModel::dismissError,
                onResume = viewModel::resumePrinter,
                onLayout = viewModel::selectCollageLayout,
                onBorders = viewModel::setCollageBorders,
                onTransformPhoto = viewModel::applyCollagePhotoGesture,
                onEditPhoto = viewModel::openCollagePhoto,
                onRandomize = viewModel::randomizeCollagePhotos,
                modifier = Modifier.padding(innerPadding),
            )

            else -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) {
                    ErrorBanner(
                        message = error,
                        showResume = connected,
                        onDismiss = viewModel::dismissError,
                        onResume = viewModel::resumePrinter,
                    )
                }

                PhotoEditorCard(
                    editor = editor,
                    onChoose = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onCollage = {
                        pickCollage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onRotate = { viewModel.rotate(clockwise = true) },
                    onReset = viewModel::resetFraming,
                    onFit = viewModel::setFit,
                    onZoom = viewModel::setZoom,
                    onTransform = viewModel::applyTransform,
                )

                PrintSettingsCard(
                    editor = editor,
                    onCopies = viewModel::setCopies,
                )

                PrinterCard(
                    printer = printer,
                    onConnect = ::connect,
                    onDisconnect = viewModel::disconnect,
                    onRefresh = viewModel::refresh,
                    onResume = viewModel::resumePrinter,
                    onSettings = { openBluetoothSettings(context) },
                )

                JobCard(
                    printer = printer,
                    onCancel = viewModel::cancelJob,
                    onDismiss = viewModel::clearCompletedJob,
                )

                DiagnosticsCard(log = printer.log)

                Text(
                    "Unofficial app · not affiliated with Liene or Hannto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmPrint && collage == null && editor.source != null) {
        PrintConfirmationDialog(
            editor = editor,
            printer = printer,
            onDismiss = { confirmPrint = false },
            onConfirm = {
                confirmPrint = false
                printAction { viewModel.startPrint() }
            },
        )
    }
}

@Composable
private fun CollageLoadingBar() {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Preparing collage…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CollagePhotoFrameBar(
    onReplace: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReplace) { Text("Replace") }
            TextButton(onClick = onRotate) { Text("Rotate 90°") }
            TextButton(onClick = onReset) { Text("Reset") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
internal fun CollagePhotoFrameContent(
    collage: CollageState,
    index: Int,
    onGesture: (Float, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = collage.photos.getOrNull(index) ?: return
    val renderLayout = if (collage.borders) {
        collage.layout
    } else {
        collageLayoutsFor(collage.photos.size, borders = false)
            .firstOrNull { it.id == collage.layout.id }
            ?: collage.layout
    }
    val cell = renderLayout.cells.getOrNull(index) ?: return
    val cellAspect = (cell.right - cell.left) * PRINT_WIDTH.toFloat() /
        ((cell.bottom - cell.top) * PRINT_HEIGHT.toFloat())
    if (cellAspect <= 0f) return

    val rotated = remember(photo.bitmap, photo.frame.rotation) {
        ImageStore.rotate(photo.bitmap, photo.frame.rotation)
    }
    val image = remember(rotated) { rotated.asImageBitmap() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Photo ${index + 1} of ${collage.photos.size}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Pinch to zoom and drag to choose the part of the photo to print.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            var viewportWidth = maxWidth
            var viewportHeight = viewportWidth / cellAspect
            if (viewportHeight > 430.dp) {
                viewportHeight = 430.dp
                viewportWidth = viewportHeight * cellAspect
            }
            Box(
                modifier = Modifier
                    .size(viewportWidth, viewportHeight)
                    .clipToBounds()
                    .background(Color.White)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    .testTag("collage-frame-viewport")
                    .pointerInput(index, photo.bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onGesture(
                                zoom,
                                pan.x,
                                pan.y,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val rect = computeDrawRect(
                        sourceWidth = image.width,
                        sourceHeight = image.height,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        fit = Fit.Cover,
                        zoom = photo.frame.zoom,
                        panX = photo.frame.panX,
                        panY = photo.frame.panY,
                    )
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(
                            rect.width.roundToInt().coerceAtLeast(1),
                            rect.height.roundToInt().coerceAtLeast(1),
                        ),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        }
        Text(
            "Rotate and Replace apply only to this photo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CollageEditorBar(collage: CollageState, onPrint: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${collage.photos.size} photos · ${collage.layout.name}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "$PRINT_WIDTH×$PRINT_HEIGHT px · 2×3 in",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onPrint,
                enabled = !collage.preparing,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    "Print",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun CollagePreviewBar(
    collage: CollageState,
    printer: PrinterUiState,
    onPrint: () -> Unit,
) {
    val connected = printer.connection.connected
    val ready = connected &&
        !printer.busy &&
        printer.status?.hasAlert != true &&
        !collage.preparing
    val reason = when {
        !connected -> "Connect the printer to print"
        printer.status?.hasAlert == true -> "Resolve the printer alert first"
        printer.busy -> "Printing…"
        collage.preparing -> "Preparing…"
        else -> "Ready"
    }
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${collage.copies} ${if (collage.copies == 1) "copy" else "copies"} · " +
                        "$PRINT_WIDTH×$PRINT_HEIGHT",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onPrint,
                enabled = ready,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    if (collage.preparing) "Preparing…" else "Print",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun CollageEditorContent(
    collage: CollageState,
    error: String?,
    connected: Boolean,
    onDismissError: () -> Unit,
    onResume: () -> Unit,
    onLayout: (CollageLayout) -> Unit,
    onBorders: (Boolean) -> Unit,
    onTransformPhoto: (Int, Float, Float, Float, Float, Float) -> Unit,
    onEditPhoto: (Int) -> Unit,
    onRandomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderLayout = if (collage.borders) {
        collage.layout
    } else {
        collageLayoutsFor(collage.photos.size, borders = false)
            .firstOrNull { it.id == collage.layout.id }
            ?: collage.layout
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) {
            ErrorBanner(
                message = error,
                showResume = connected,
                onDismiss = onDismissError,
                onResume = onResume,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(2f / 3f)
                    .background(Color.White)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    .testTag("collage-preview")
                    .pointerInput(collage.layout.id, collage.borders, collage.photos.size) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val nx = centroid.x / size.width
                            val ny = centroid.y / size.height
                            val index = renderLayout.cells.indexOfFirst { cell ->
                                nx >= cell.left && nx <= cell.right &&
                                    ny >= cell.top && ny <= cell.bottom
                            }
                            if (index >= 0) {
                                onTransformPhoto(
                                    index,
                                    zoom,
                                    pan.x,
                                    pan.y,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                            }
                        }
                    }
                    .pointerInput(collage.layout.id, collage.borders, collage.photos.size) {
                        detectTapGestures { position ->
                            val nx = position.x / size.width
                            val ny = position.y / size.height
                            val index = renderLayout.cells.indexOfFirst { cell ->
                                nx >= cell.left && nx <= cell.right &&
                                    ny >= cell.top && ny <= cell.bottom
                            }
                            if (index >= 0) onEditPhoto(index)
                        }
                    },
            ) {
                Image(
                    bitmap = collage.preview.asImageBitmap(),
                    contentDescription = "Collage preview — pinch or drag a photo to reframe it",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            "Pinch or drag a photo to reframe it; tap one to rotate or fine-tune.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Layout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Checkbox(
                checked = collage.borders,
                onCheckedChange = onBorders,
                modifier = Modifier.testTag("collage-borders"),
            )
            Text("Borders", style = MaterialTheme.typography.bodyMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(collage.layouts, key = { it.id }) { layout ->
                LayoutOption(
                    layout = layout,
                    selected = layout.id == collage.layout.id,
                    onClick = { onLayout(layout) },
                )
            }
        }
        TextButton(
            onClick = onRandomize,
            enabled = collage.photos.size > 1,
        ) {
            Text("Randomize photos")
        }
        Text(
            "Pick a layout, then print. Randomize shuffles the photos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LayoutOption(
    layout: CollageLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val cellColor = Color(0xFFBDBDBD)
    Box(
        modifier = Modifier
            .width(78.dp)
            .aspectRatio(2f / 3f)
            .background(Color.White)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outline,
            )
            .testTag("layout-${layout.id}")
            .clickable(
                role = Role.Button,
                onClickLabel = layout.name,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            layout.cells.forEach { cell ->
                drawRect(
                    color = cellColor,
                    topLeft = Offset(cell.left * size.width, cell.top * size.height),
                    size = Size(
                        (cell.right - cell.left) * size.width,
                        (cell.bottom - cell.top) * size.height,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun CollagePrintPreviewContent(
    collage: CollageState,
    printer: PrinterUiState,
    error: String?,
    onCopies: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onDismissError: () -> Unit,
    onCancelJob: () -> Unit,
    onDismissJob: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) {
            ErrorBanner(
                message = error,
                showResume = printer.connection.connected,
                onDismiss = onDismissError,
                onResume = onResume,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = collage.preview.asImageBitmap(),
                contentDescription = "Collage print preview",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(2f / 3f)
                    .background(Color.White)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                contentScale = ContentScale.Fit,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Prints",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copies", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onCopies(collage.copies - 1) },
                        enabled = collage.copies > 1,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp),
                    ) { Text("−") }
                    Text(
                        collage.copies.toString(),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(
                        onClick = { onCopies(collage.copies + 1) },
                        enabled = collage.copies < 9,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp),
                    ) { Text("+") }
                }
                Text(
                    "$PRINT_WIDTH × $PRINT_HEIGHT px · ${collage.layout.name} · " +
                        if (collage.borders) "borders" else "no borders",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PrinterCard(
            printer = printer,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh,
            onResume = onResume,
            onSettings = onSettings,
        )

        JobCard(
            printer = printer,
            onCancel = onCancelJob,
            onDismiss = onDismissJob,
        )

        DiagnosticsCard(log = printer.log)
    }
}

@Composable
private fun PrintConfirmationDialog(
    editor: EditorState,
    printer: PrinterUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val battery = printer.status?.battery
    val canConfirm = printer.connection.connected &&
        !printer.busy &&
        printer.status?.hasAlert != true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print this photo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PrintPreview(
                        editor = editor,
                        modifier = Modifier
                            .width(170.dp)
                            .aspectRatio(2f / 3f),
                    )
                }
                Text(
                    "Final 2×3 composition",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SummaryRow("Resolution", "$PRINT_WIDTH × $PRINT_HEIGHT px")
                SummaryRow(
                    "Framing",
                    if (editor.fit == Fit.Contain) "Fit (white borders)" else "Fill (no borders)",
                )
                SummaryRow("Copies", editor.copies.toString())
                printer.connection.name?.let { SummaryRow("Printer", it) }
                if (battery != null && isBatteryLow(battery)) {
                    Text(
                        if (battery == 0) {
                            "Printer battery is empty. Connect power before printing."
                        } else {
                            "Printer battery is low. Connect power for a reliable print."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!canConfirm) {
                    Text(
                        "The printer is no longer ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = canConfirm) {
                Text("Print now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrintPreview(editor: EditorState, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color.White)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
    ) {
        val preview = editor.preview
        if (preview == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            val image = remember(preview) { preview.asImageBitmap() }
            Canvas(Modifier.fillMaxSize()) {
                val rect = computeDrawRect(
                    sourceWidth = image.width,
                    sourceHeight = image.height,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    fit = editor.fit,
                    zoom = editor.zoom,
                    panX = editor.panX,
                    panY = editor.panY,
                )
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(
                        rect.width.roundToInt().coerceAtLeast(1),
                        rect.height.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

@Composable
private fun PhotoEditorCard(
    editor: EditorState,
    onChoose: () -> Unit,
    onCollage: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onFit: (Fit) -> Unit,
    onZoom: (Float) -> Unit,
    onTransform: (Float, Float, Float, Float, Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCollage) { Text("Collage") }
                TextButton(onClick = onChoose) { Text("Choose") }
            }

            when {
                editor.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                editor.source == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            "Share a photo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "From any gallery app, tap Share and choose Liene. " +
                                "Or pick a photo here.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onChoose) { Text("Choose photo") }
                    }
                }

                else -> {
                    FramedPreview(editor = editor, onTransform = onTransform)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRotate,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) { Text("Rotate 90°") }
                        TextButton(onClick = onReset) { Text("Reset") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${editor.rotation}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Framing", style = MaterialTheme.typography.bodyMedium)
                        FilterChip(
                            selected = editor.fit == Fit.Contain,
                            onClick = { onFit(Fit.Contain) },
                            label = { Text("Fit") },
                        )
                        FilterChip(
                            selected = editor.fit == Fit.Cover,
                            onClick = { onFit(Fit.Cover) },
                            label = { Text("Fill") },
                        )
                    }

                    if (editor.fit == Fit.Cover) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Zoom",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = editor.zoom,
                                onValueChange = onZoom,
                                valueRange = 1f..8f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                            Text(
                                String.format(Locale.US, "%.1f×", editor.zoom),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Pinch to zoom and drag to frame the 2:3 print.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "The whole photo is printed, with white borders where needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FramedPreview(
    editor: EditorState,
    onTransform: (Float, Float, Float, Float, Float) -> Unit,
) {
    val preview = editor.preview
    if (preview == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    val image = remember(preview) { preview.asImageBitmap() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(Color.White)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            .pointerInput(editor.fit, image.width, image.height) {
                if (editor.fit == Fit.Cover) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onTransform(
                            zoom,
                            pan.x,
                            pan.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rect = computeDrawRect(
                sourceWidth = image.width,
                sourceHeight = image.height,
                canvasWidth = size.width,
                canvasHeight = size.height,
                fit = editor.fit,
                zoom = editor.zoom,
                panX = editor.panX,
                panY = editor.panY,
            )
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(
                    rect.width.roundToInt().coerceAtLeast(1),
                    rect.height.roundToInt().coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.High,
            )
        }

        if (editor.fit == Fit.Cover && editor.zoom > 1.01f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    String.format(Locale.US, "%.1f×", editor.zoom),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PrintSettingsCard(
    editor: EditorState,
    onCopies: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Print settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Copies", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onCopies(editor.copies - 1) },
                    enabled = editor.copies > 1,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp),
                ) { Text("−") }
                Text(
                    editor.copies.toString(),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = { onCopies(editor.copies + 1) },
                    enabled = editor.copies < 9,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp),
                ) { Text("+") }
            }

            Text(
                "$PRINT_WIDTH × $PRINT_HEIGHT px · 2×3 in",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrinterCard(
    printer: PrinterUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
) {
    val status = printer.status
    val connection = printer.connection
    val statusColor = when {
        status?.hasAlert == true || status?.state == 60 -> MaterialTheme.colorScheme.error
        status?.state == 40 -> MaterialTheme.colorScheme.tertiary
        status?.state == 20 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Printer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (connection.connected) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        status?.headline() ?: "Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                    )
                }
            }

            when {
                connection.connecting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                connection.connected -> {
                    Text(
                        connection.name ?: "Printer",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    status?.battery?.let {
                        Text(
                            batteryLabel(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    printer.info?.let { info ->
                        Text(
                            "${info.model} · ${info.firmware}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Serial ${info.serial}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    status?.alerts?.forEach { code ->
                        Text(
                            io.github.bydavy.liene.printer.alertLabel(code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (status?.hasAlert == true) {
                        Button(onClick = onResume) { Text("Resume printer") }
                    }
                    Row {
                        TextButton(onClick = onRefresh, enabled = !printer.busy) {
                            Text("Refresh")
                        }
                        TextButton(onClick = onDisconnect, enabled = !printer.busy) {
                            Text("Disconnect")
                        }
                    }
                }

                else -> {
                    Text(
                        "Connect to see battery, alerts, and printer status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onConnect) { Text("Connect") }
                        TextButton(onClick = onSettings) { Text("Bluetooth settings") }
                    }
                    Text(
                        "Pair the printer in Android's Bluetooth settings first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    printer: PrinterUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val job = printer.job
    if (!printer.busy && job == null) return

    val uploading = printer.uploadTotal > 0 && printer.uploadSent < printer.uploadTotal
    val uploadFraction = if (printer.uploadTotal > 0) {
        printer.uploadSent.toFloat() / printer.uploadTotal
    } else {
        0f
    }
    val printFraction = job?.let {
        if (it.fileSize > 0) {
            (it.transferSize.toFloat() / it.fileSize).coerceIn(0f, 1f)
        } else {
            0f
        }
    } ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (job != null) "Print job #${job.jobId}" else "Print job",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (printer.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        job?.let { jobStateLabel(it.state) } ?: "Done",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            when {
                uploading -> {
                    Text(
                        "Uploading ${printer.uploadSent}/${printer.uploadTotal} frames",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { uploadFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                job != null -> {
                    Text(
                        jobStateLabel(job.state) +
                            if (job.state == 3) " · page ${job.page.coerceAtLeast(1)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { printFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${job.transferSize} / ${job.fileSize} bytes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (job.state == 9) {
                        Text(
                            "Finished in ${"%.1f".format(Locale.US, job.printTimeMs / 1000f)} s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (printer.busy) {
                    TextButton(onClick = onCancel) { Text("Cancel job") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    showResume: Boolean,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (showResume) {
                    Button(onClick = onResume) { Text("Resume printer") }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(log: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                if (log.isEmpty()) {
                    Text(
                        "No log entries yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .height(180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        log.takeLast(80).forEach { line ->
                            Text(
                                line,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintBar(
    editor: EditorState,
    printer: PrinterUiState,
    canPrint: Boolean,
    onPrint: () -> Unit,
) {
    val reason = when {
        editor.source == null -> "Choose a photo"
        !printer.connection.connected ->
            if (printer.connection.connecting) "Connecting…" else "Connect the printer to print"
        printer.status?.hasAlert == true -> "Resolve the printer alert first"
        printer.busy -> "Printing…"
        else -> "Ready"
    }

    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$PRINT_WIDTH×$PRINT_HEIGHT px · ${editor.fit.label}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onPrint,
                enabled = canPrint,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    if (editor.preparing) "Preparing…" else "Print",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

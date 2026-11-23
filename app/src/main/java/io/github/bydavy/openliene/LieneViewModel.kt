package io.github.bydavy.openliene

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bydavy.openliene.image.CollageLayout
import io.github.bydavy.openliene.image.CollagePhoto
import io.github.bydavy.openliene.image.EditorState
import io.github.bydavy.openliene.image.Fit
import io.github.bydavy.openliene.image.ImageStore
import io.github.bydavy.openliene.image.PRINT_HEIGHT
import io.github.bydavy.openliene.image.PRINT_WIDTH
import io.github.bydavy.openliene.image.PhotoFrame
import io.github.bydavy.openliene.image.applyGesture
import io.github.bydavy.openliene.image.applyPhotoGesture
import io.github.bydavy.openliene.image.collageLayoutsFor
import io.github.bydavy.openliene.image.prepareJpeg
import io.github.bydavy.openliene.image.renderCollage
import io.github.bydavy.openliene.image.renderCollageJpeg
import io.github.bydavy.openliene.printer.PrintService
import io.github.bydavy.openliene.printer.PrinterUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CollageStage {
    Editor,
    PrintPreview,
}

data class CollageState(
    val photos: List<CollagePhoto>,
    val preview: Bitmap,
    val layouts: List<CollageLayout>,
    val layout: CollageLayout,
    val borders: Boolean = true,
    val copies: Int = 1,
    val editingPhoto: Int? = null,
    val preparing: Boolean = false,
    val error: String? = null,
)

class LieneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LieneApplication).repository
    val printer: StateFlow<PrinterUiState> = repository.state

    private val _editor = MutableStateFlow(EditorState())
    val editor: StateFlow<EditorState> = _editor.asStateFlow()

    private val _collage = MutableStateFlow<CollageState?>(null)
    val collage: StateFlow<CollageState?> = _collage.asStateFlow()

    private val _collageStage = MutableStateFlow(CollageStage.Editor)
    val collageStage: StateFlow<CollageStage> = _collageStage.asStateFlow()

    private val _collageLoading = MutableStateFlow(false)
    val collageLoading: StateFlow<Boolean> = _collageLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var previewJob: Job? = null
    private var collagePreviewJob: Job? = null
    private var collageLoadJob: Job? = null

    fun connect() = repository.connect()
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
    fun resumePrinter() = repository.resumePrinter()
    fun cancelJob() = repository.cancelJob()

    fun dismissError() {
        _message.value = null
        _editor.update { it.copy(error = null) }
        _collage.update { it?.copy(error = null) }
        repository.clearError()
    }

    fun reportError(message: String) {
        _message.value = message
    }

    fun clearCompletedJob() = repository.clearJob()

    fun loadImage(uri: Uri) {
        if (_editor.value.loading) return
        _collage.value = null
        _collageStage.value = CollageStage.Editor
        _collageLoading.value = false
        collagePreviewJob?.cancel()
        collageLoadJob?.cancel()
        _editor.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val (file, name) = withContext(Dispatchers.IO) {
                    ImageStore.copyToCache(getApplication(), uri)
                }
                val source = withContext(Dispatchers.IO) {
                    ImageStore.loadOriented(file)
                }
                val preview = withContext(Dispatchers.IO) {
                    ImageStore.preview(source, rotation = 0)
                }
                runCatching { file.delete() }
                _editor.value = EditorState(
                    source = source,
                    preview = preview,
                    fileName = name,
                )
            } catch (e: Exception) {
                _editor.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Could not load that photo",
                    )
                }
            }
        }
    }

    fun loadCollage(uris: List<Uri>) {
        if (uris.size < 2) {
            _message.value = "Select at least two photos for a collage."
            return
        }
        val selected = uris.take(4)
        collageLoadJob?.cancel()
        collagePreviewJob?.cancel()
        _collage.value = null
        _collageStage.value = CollageStage.Editor
        _collageLoading.value = true
        _editor.update { it.copy(loading = false, error = null) }

        collageLoadJob = viewModelScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    selected.map { uri ->
                        val (file, _) = ImageStore.copyToCache(getApplication(), uri)
                        try {
                            CollagePhoto(ImageStore.loadOriented(file, maxDimension = 1600))
                        } finally {
                            file.delete()
                        }
                    }
                }
                if (photos.size < 2) {
                    throw IllegalArgumentException("Select at least two readable photos")
                }
                val layouts = collageLayoutsFor(photos.size, borders = true)
                val layout = layouts.first()
                val preview = withContext(Dispatchers.IO) {
                    renderCollage(photos, layout, 600, 900)
                }
                _collage.value = CollageState(
                    photos = photos,
                    preview = preview,
                    layouts = layouts,
                    layout = layout,
                )
                _collageStage.value = CollageStage.Editor
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not build the collage"
            } finally {
                _collageLoading.value = false
            }
        }
    }

    fun selectCollageLayout(layout: CollageLayout) {
        val state = _collage.value ?: return
        if (state.layout.id == layout.id) return
        _collage.value = state.copy(layout = layout, error = null)
        refreshCollagePreview(state.photos, layout, state.borders)
    }

    fun randomizeCollagePhotos() {
        val state = _collage.value ?: return
        if (state.photos.size < 2) return
        var photos = state.photos.shuffled()
        if (photos == state.photos) {
            photos = state.photos.drop(1) + state.photos.first()
        }
        _collage.value = state.copy(photos = photos, error = null)
        refreshCollagePreview(photos, state.layout, state.borders)
    }

    fun setCollageCopies(copies: Int) {
        _collage.update { it?.copy(copies = copies.coerceIn(1, 9)) }
    }

    fun setCollageBorders(borders: Boolean) {
        val state = _collage.value ?: return
        if (state.borders == borders) return
        _collage.value = state.copy(borders = borders, error = null)
        refreshCollagePreview(state.photos, state.layout, borders)
    }

    private fun refreshCollagePreview(
        photos: List<CollagePhoto>,
        layout: CollageLayout,
        borders: Boolean,
    ) {
        collagePreviewJob?.cancel()
        collagePreviewJob = viewModelScope.launch {
            val renderLayout = layoutForRender(photos.size, layout, borders)
            val preview = withContext(Dispatchers.IO) {
                renderCollage(photos, renderLayout, 600, 900)
            }
            _collage.update { current ->
                if (current?.layout?.id == layout.id &&
                    current.photos == photos &&
                    current.borders == borders
                ) {
                    current.copy(preview = preview)
                } else {
                    current
                }
            }
        }
    }

    fun openCollagePhoto(index: Int) {
        _collage.update { state ->
            if (state != null && index in state.photos.indices) {
                state.copy(editingPhoto = index)
            } else {
                state
            }
        }
    }

    fun closeCollagePhotoEditor() {
        val state = _collage.value ?: return
        if (state.editingPhoto == null) return
        _collage.value = state.copy(editingPhoto = null)
        refreshCollagePreview(state.photos, state.layout, state.borders)
    }

    fun resetCollagePhoto(index: Int) {
        updateCollageFrame(index) { PhotoFrame() }
    }

    fun replaceCollagePhoto(index: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.IO) {
                    val (file, _) = ImageStore.copyToCache(getApplication(), uri)
                    try {
                        CollagePhoto(ImageStore.loadOriented(file, maxDimension = 1600))
                    } finally {
                        file.delete()
                    }
                }
                _collage.update { state ->
                    if (state == null || index !in state.photos.indices) {
                        state
                    } else {
                        val photos = state.photos.toMutableList()
                        photos[index] = photo
                        state.copy(photos = photos, error = null)
                    }
                }
                _collage.value?.let { current ->
                    refreshCollagePreview(current.photos, current.layout, current.borders)
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not replace that photo"
            }
        }
    }

    fun rotateCollagePhoto(index: Int) {
        updateCollageFrame(index) { frame ->
            frame.copy(
                rotation = (frame.rotation + 90) % 360,
                zoom = 1f,
                panX = 0f,
                panY = 0f,
            )
        }
    }

    private fun updateCollageFrame(index: Int, update: (PhotoFrame) -> PhotoFrame) {
        _collage.update { state ->
            if (state == null || index !in state.photos.indices) {
                state
            } else {
                val photos = state.photos.toMutableList()
                photos[index] = photos[index].copy(frame = update(photos[index].frame))
                state.copy(photos = photos)
            }
        }
        _collage.value?.let { current ->
            refreshCollagePreview(current.photos, current.layout, current.borders)
        }
    }

    fun applyCollagePhotoGesture(
        index: Int,
        zoomChange: Float,
        panPxX: Float,
        panPxY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        _collage.update { state ->
            if (state == null || index !in state.photos.indices) {
                state
            } else {
                val photos = state.photos.toMutableList()
                val photo = photos[index]
                photos[index] = photo.copy(
                    frame = applyPhotoGesture(
                        frame = photo.frame,
                        zoomChange = zoomChange,
                        panPxX = panPxX,
                        panPxY = panPxY,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        sourceWidth = photo.bitmap.width,
                        sourceHeight = photo.bitmap.height,
                    ),
                )
                state.copy(photos = photos)
            }
        }
    }

    private fun layoutForRender(
        photoCount: Int,
        layout: CollageLayout,
        borders: Boolean,
    ): CollageLayout =
        if (borders) {
            layout
        } else {
            collageLayoutsFor(photoCount, borders = false)
                .firstOrNull { it.id == layout.id }
                ?: layout
        }

    fun openCollagePreview() {
        if (_collage.value != null) {
            _collageStage.value = CollageStage.PrintPreview
        }
    }

    fun backToCollageEditor() {
        if (_collage.value != null) {
            _collageStage.value = CollageStage.Editor
        }
    }

    fun closeCollage() {
        collageLoadJob?.cancel()
        collagePreviewJob?.cancel()
        _collageLoading.value = false
        _collage.value = null
        _collageStage.value = CollageStage.Editor
    }

    fun startCollagePrint() {
        val state = _collage.value ?: return
        if (state.preparing || printer.value.busy) return
        _collage.update { it?.copy(preparing = true, error = null) }
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    renderCollageJpeg(
                        state.photos,
                        layoutForRender(state.photos.size, state.layout, state.borders),
                    )
                }
                val file = File(getApplication<Application>().cacheDir, "liene-collage.jpg")
                withContext(Dispatchers.IO) { file.writeBytes(jpeg) }
                repository.clearJob()
                PrintService.start(getApplication(), file, state.copies)
            } catch (e: Exception) {
                _collage.update {
                    it?.copy(error = e.message ?: "Could not prepare the collage")
                }
            } finally {
                _collage.update { it?.copy(preparing = false) }
            }
        }
    }

    fun rotate(clockwise: Boolean = true) {
        val current = _editor.value
        val source = current.source ?: return
        val next = current.rotated(clockwise)
        _editor.value = next
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                ImageStore.preview(source, next.rotation)
            }
            if (_editor.value.rotation == next.rotation) {
                _editor.update { it.copy(preview = preview) }
            }
        }
    }

    fun resetFraming() {
        val current = _editor.value
        val source = current.source ?: return
        val next = current.resetFraming()
        _editor.value = next
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                ImageStore.preview(source, rotation = 0)
            }
            if (_editor.value.rotation == 0) {
                _editor.update { it.copy(preview = preview) }
            }
        }
    }

    fun setFit(fit: Fit) {
        _editor.update {
            if (it.fit == fit) it else it.copy(fit = fit, zoom = 1f, panX = 0f, panY = 0f)
        }
    }

    fun setCopies(copies: Int) {
        _editor.update { it.copy(copies = copies.coerceIn(1, 9)) }
    }

    fun setZoom(zoom: Float) {
        _editor.update {
            if (it.fit == Fit.Cover) {
                it.copy(zoom = zoom.coerceIn(1f, 8f))
            } else {
                it
            }
        }
    }

    fun applyTransform(
        zoomChange: Float,
        panPxX: Float,
        panPxY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        _editor.update { state ->
            val preview = state.preview ?: return@update state
            applyGesture(
                state = state,
                zoomChange = zoomChange,
                panPxX = panPxX,
                panPxY = panPxY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                sourceWidth = preview.width,
                sourceHeight = preview.height,
            )
        }
    }

    fun startPrint() {
        val state = _editor.value
        val source = state.source ?: return
        if (state.preparing || printer.value.busy) return

        _editor.update { it.copy(preparing = true, error = null) }
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    prepareJpeg(
                        source = source,
                        rotation = state.rotation,
                        fit = state.fit,
                        zoom = state.zoom,
                        panX = state.panX,
                        panY = state.panY,
                        width = PRINT_WIDTH,
                        height = PRINT_HEIGHT,
                    )
                }
                val file = File(getApplication<Application>().cacheDir, "liene-print.jpg")
                withContext(Dispatchers.IO) { file.writeBytes(jpeg) }
                repository.clearJob()
                PrintService.start(getApplication(), file, state.copies)
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not prepare the photo for printing"
            } finally {
                _editor.update { it.copy(preparing = false) }
            }
        }
    }
}

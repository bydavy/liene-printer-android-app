package io.github.bydavy.liene

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bydavy.liene.image.EditorState
import io.github.bydavy.liene.image.Fit
import io.github.bydavy.liene.image.ImageStore
import io.github.bydavy.liene.image.Quality
import io.github.bydavy.liene.image.applyGesture
import io.github.bydavy.liene.image.prepareJpeg
import io.github.bydavy.liene.printer.PrintService
import io.github.bydavy.liene.printer.PrinterUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LieneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LieneApplication).repository
    val printer: StateFlow<PrinterUiState> = repository.state

    private val _editor = MutableStateFlow(EditorState())
    val editor: StateFlow<EditorState> = _editor.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var previewJob: Job? = null

    fun connect() = repository.connect()
    fun disconnect() = repository.disconnect()
    fun refresh() = repository.refresh()
    fun resumePrinter() = repository.resumePrinter()
    fun cancelJob() = repository.cancelJob()

    fun dismissError() {
        _message.value = null
        _editor.update { it.copy(error = null) }
        repository.clearError()
    }

    fun reportError(message: String) {
        _message.value = message
    }

    fun clearCompletedJob() = repository.clearJob()

    fun loadImage(uri: Uri) {
        if (_editor.value.loading) return
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

    fun setQuality(quality: Quality) {
        _editor.update { it.copy(quality = quality) }
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
                        width = state.quality.width,
                        height = state.quality.height,
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

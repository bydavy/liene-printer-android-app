package io.github.bydavy.liene.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.bydavy.liene.protocol.Methods
import io.github.bydavy.liene.protocol.STATUS_PROPS
import io.github.bydavy.liene.protocol.INFO_PROPS
import io.github.bydavy.liene.protocol.cancelJobParams
import io.github.bydavy.liene.protocol.dataFrames
import io.github.bydavy.liene.protocol.getPropParams
import io.github.bydavy.liene.protocol.jobInfoParams
import io.github.bydavy.liene.protocol.parseJobInfo
import io.github.bydavy.liene.protocol.printJobParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PrinterRepository(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PrinterUiState())
    val state: StateFlow<PrinterUiState> = _state.asStateFlow()

    private val printer: BluetoothPrinter? =
        adapter?.let { BluetoothPrinter(it) { line -> appendLog(line) } }

    private val printMutex = Mutex()
    private var connectJob: Job? = null
    private var refreshJob: Job? = null

    private val timestamp = DateTimeFormatter.ofPattern("HH:mm:ss")

    @SuppressLint("MissingPermission")
    fun connect() {
        val current = _state.value
        if (current.connection.connected || current.connection.connecting || current.busy) return
        val p = printer ?: run {
            setError("This device does not expose a Bluetooth adapter.")
            return
        }
        if (runCatching { adapter?.isEnabled == true }.getOrDefault(false).not()) {
            setError("Bluetooth is off or access was denied. Turn it on, then connect again.")
            return
        }

        connectJob = scope.launch {
            _state.update {
                it.copy(connection = it.connection.copy(connecting = true), error = null)
            }
            try {
                val device = p.preferredPrinter()
                    ?: throw IOException(
                        "No paired Liene printer found. Pair it in Android's Bluetooth " +
                            "settings first, then tap Connect.",
                    )
                p.connect(device)
                _state.update {
                    it.copy(
                        connection = ConnectionState(
                            connected = true,
                            name = device.name ?: "Printer",
                            address = device.address,
                        ),
                    )
                }
                appendLog("Connected to ${device.name ?: device.address}")
                runCatching { refreshStatusInternal(p) }
                    .onFailure { appendLog("Status unavailable: ${humanError(it)}") }
                runCatching { refreshInfoInternal(p) }
                    .onFailure { appendLog("Device info unavailable: ${humanError(it)}") }
            } catch (e: Exception) {
                runCatching { p.close() }
                _state.update {
                    it.copy(connection = ConnectionState(), error = humanError(e))
                }
                appendLog("Connection failed: ${humanError(e)}")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        refreshJob?.cancel()
        scope.launch {
            runCatching { printer?.close() }
            _state.update {
                it.copy(
                    connection = ConnectionState(),
                    status = null,
                    info = null,
                    job = null,
                    uploadSent = 0,
                    uploadTotal = 0,
                )
            }
            appendLog("Disconnected")
        }
    }

    fun refresh() {
        val current = _state.value
        if (!current.connection.connected || current.busy || refreshJob?.isActive == true) return
        val p = printer ?: return
        refreshJob = scope.launch {
            runCatching { refreshStatusInternal(p) }
                .onFailure { appendLog("Status refresh failed: ${humanError(it)}") }
            runCatching { refreshInfoInternal(p) }
                .onFailure { appendLog("Info refresh failed: ${humanError(it)}") }
            if (!p.isConnected) {
                _state.update { it.copy(connection = ConnectionState()) }
            }
        }
    }

    fun resumePrinter() {
        val p = printer ?: return
        scope.launch {
            try {
                p.request(Methods.RESUME_PRINTER, JSONArray(), 20_000)
                appendLog("Printer resumed")
                delay(1_000)
                refreshStatusInternal(p)
            } catch (e: Exception) {
                setError(humanError(e))
            }
        }
    }

    fun cancelJob() {
        val jobId = _state.value.job?.jobId ?: return
        val p = printer ?: return
        scope.launch {
            try {
                p.request(Methods.CANCEL_JOB, cancelJobParams(jobId), 20_000)
                appendLog("Cancellation requested for job #$jobId")
            } catch (e: Exception) {
                setError(humanError(e))
            }
        }
    }

    /** Runs in the foreground service; keeps the printer alive for the job. */
    @SuppressLint("MissingPermission")
    suspend fun print(file: File, copies: Int): Boolean = printMutex.withLock {
        val p = printer ?: run {
            setError("This device does not expose a Bluetooth adapter.")
            return@withLock false
        }

        _state.update {
            it.copy(
                busy = true,
                error = null,
                job = null,
                uploadSent = 0,
                uploadTotal = 0,
            )
        }

        var success = false
        try {
            if (!p.isConnected) {
                val device = p.preferredPrinter()
                    ?: throw IOException(
                        "No paired Liene printer found. Pair it in Android's Bluetooth " +
                            "settings first.",
                    )
                p.connect(device)
                _state.update {
                    it.copy(
                        connection = ConnectionState(
                            connected = true,
                            name = device.name ?: "Printer",
                            address = device.address,
                        ),
                    )
                }
            }

            val data = file.readBytes()
            if (data.isEmpty()) throw IOException("Prepared print file is empty")
            val response = p.request(
                Methods.PRINT_JOB,
                printJobParams(copies, file.name, data.size.toLong()),
                20_000,
            )
            val jobId = response.jobId()
                ?: throw IOException("The printer did not return a job id")
            val frames = dataFrames(jobId, data)
            _state.update {
                it.copy(
                    job = JobProgress(
                        jobId = jobId,
                        totalFrames = frames.size,
                        fileSize = data.size.toLong(),
                    ),
                    uploadSent = 0,
                    uploadTotal = frames.size,
                )
            }
            appendLog("Job #$jobId created (${frames.size} frames, ${data.size} bytes)")

            p.upload(frames, frameDelayMs = 20) { sent, total ->
                _state.update { it.copy(uploadSent = sent, uploadTotal = total) }
            }
            _state.update { it.copy(uploadSent = frames.size, uploadTotal = frames.size) }

            // Give the printer a moment to start consuming the job before
            // polling; it is often busy during this window.
            delay(2_000)
            watchJob(p, jobId, data.size.toLong(), frames.size)
            success = true
        } catch (e: Exception) {
            runCatching { p.close() }
            _state.update {
                it.copy(
                    connection = ConnectionState(),
                    status = null,
                    info = null,
                    error = humanError(e),
                )
            }
            appendLog("Print failed: ${humanError(e)}")
        } finally {
            _state.update { it.copy(busy = false) }
        }
        success
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearJob() {
        _state.update {
            it.copy(
                job = null,
                uploadSent = 0,
                uploadTotal = 0,
            )
        }
    }

    private suspend fun refreshStatusInternal(p: BluetoothPrinter) {
        val response = p.request(Methods.GET_PROP, getPropParams(STATUS_PROPS), 10_000)
        val status = parseStatus(response.result)
        if (status == null) throw IOException("Unexpected printer status response")
        _state.update { it.copy(status = status) }
    }

    private suspend fun refreshInfoInternal(p: BluetoothPrinter) {
        val address = _state.value.connection.address.orEmpty()
        val response = p.request(Methods.GET_PROP, getPropParams(INFO_PROPS), 10_000)
        val info = parseInfo(response.result, address)
        if (info != null) {
            _state.update { it.copy(info = info) }
        }
    }

    private suspend fun watchJob(
        p: BluetoothPrinter,
        jobId: Int,
        fileSize: Long,
        totalFrames: Int,
    ) {
        val deadline = System.currentTimeMillis() + 240_000
        var lastError: Exception? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val response = p.request(Methods.GET_JOB_INFO, jobInfoParams(jobId), 20_000)
                val info = parseJobInfo(response.result)
                if (info != null) {
                    _state.update {
                        it.copy(
                            job = JobProgress(
                                jobId = jobId,
                                totalFrames = totalFrames,
                                state = info.state,
                                reason = info.reason,
                                transferSize = info.transferSize,
                                fileSize = info.fileSize.takeIf { size -> size > 0 } ?: fileSize,
                                page = info.page,
                                printTimeMs = info.printTimeMs,
                            ),
                        )
                    }
                    if (info.isFinished) return
                }
            } catch (e: Exception) {
                lastError = e
                appendLog("Job poll failed (will retry): ${humanError(e)}")
            }
            delay(2_000)
        }

        throw IOException(
            "Timed out waiting for job #$jobId" +
                (lastError?.message?.let { ": $it" } ?: ""),
            lastError,
        )
    }

    private fun setError(message: String) {
        _state.update { it.copy(error = message) }
        appendLog("Error: $message")
    }

    private fun appendLog(line: String) {
        val stamped = "${LocalTime.now().format(timestamp)} $line"
        _state.update { current ->
            current.copy(log = (current.log + stamped).takeLast(200))
        }
    }

    private fun humanError(error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return when {
            error is SecurityException ||
                message.contains("permission", ignoreCase = true) ->
                "Bluetooth permission is required. Allow Nearby devices access and try again."
            message.contains("not connected", ignoreCase = true) ->
                "The printer is not connected."
            else -> message
        }
    }
}

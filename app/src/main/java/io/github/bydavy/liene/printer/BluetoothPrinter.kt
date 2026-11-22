package io.github.bydavy.liene.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.github.bydavy.liene.protocol.Frame
import io.github.bydavy.liene.protocol.FrameReader
import io.github.bydavy.liene.protocol.RpcResponse
import io.github.bydavy.liene.protocol.SPP_UUID
import io.github.bydavy.liene.protocol.parseResponse
import io.github.bydavy.liene.protocol.requestJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Bluetooth Classic SPP transport for the printer.
 *
 * The printer's data channel is RFCOMM channel 1. Android's public API goes
 * through SDP first; some printer firmwares do not answer that query, so we
 * also try the direct channel as a fallback (the same approach as the desktop
 * client).
 */
class BluetoothPrinter(
    private val adapter: BluetoothAdapter,
    private val log: (String) -> Unit,
) {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var reader = FrameReader()
    private var nextId = 1
    private val ioMutex = Mutex()

    // BluetoothSocket reads are blocking, so a dedicated reader thread feeds
    // complete frames into a queue. Requests poll the queue with a deadline.
    private val incoming = LinkedBlockingQueue<Frame>()
    private val readerLock = Any()
    @Volatile private var reading = false
    @Volatile private var readError: IOException? = null
    private var readThread: Thread? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && readError == null

    @SuppressLint("MissingPermission")
    fun pairedPrinters(): List<BluetoothDevice> =
        runCatching { adapter.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    fun preferredPrinter(): BluetoothDevice? {
        val devices = pairedPrinters()
        return devices.firstOrNull { matches(it.name) }
            ?: devices.firstOrNull { matches(it.address) }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, timeoutMs: Int = 15_000) {
        withContext(Dispatchers.IO) {
            closeNow()
            runCatching { adapter.cancelDiscovery() }
            log("Opening Bluetooth serial channel to ${device.name ?: device.address}")

            var lastError: Exception? = null

            suspend fun attempt(label: String, factory: () -> BluetoothSocket): Boolean {
                var candidate: BluetoothSocket? = null
                return try {
                    candidate = factory()
                    connectWithWatchdog(candidate, timeoutMs)
                    socket = candidate
                    input = candidate.inputStream
                    output = candidate.outputStream
                    reader = FrameReader()
                    incoming.clear()
                    readError = null
                    reading = true
                    readThread = Thread(
                        { readLoop(candidate.inputStream) },
                        "liene-bluetooth-reader",
                    ).apply {
                        isDaemon = true
                        start()
                    }
                    log("Connected ($label)")
                    true
                } catch (e: SecurityException) {
                    runCatching { candidate?.close() }
                    throw IOException("Bluetooth permission was denied", e)
                } catch (e: Exception) {
                    lastError = e
                    log("$label failed: ${e.message ?: e.javaClass.simpleName}")
                    runCatching { candidate?.close() }
                    delay(500)
                    false
                }
            }

            if (attempt("SPP service record") {
                    device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                }) {
                return@withContext
            }
            if (attempt("insecure SPP service record") {
                    device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                }) {
                return@withContext
            }
            if (attempt("RFCOMM channel 1") { channelSocket(device, insecure = false) }) {
                return@withContext
            }
            if (attempt("insecure RFCOMM channel 1") { channelSocket(device, insecure = true) }) {
                return@withContext
            }

            throw IOException(
                "Could not open the printer's Bluetooth serial channel" +
                    (lastError?.message?.let { ": $it" } ?: ""),
                lastError,
            )
        }
    }

    /**
     * A blocking Bluetooth connect cannot be cancelled by coroutine timeout,
     * so close the socket from a timer if it does not complete in time.
     */
    @SuppressLint("MissingPermission")
    private fun connectWithWatchdog(socket: BluetoothSocket, timeoutMs: Int) {
        val timer = Timer(true)
        timer.schedule(
            object : TimerTask() {
                override fun run() = runCatching { socket.close() }.let { }
            },
            timeoutMs.toLong(),
        )
        try {
            socket.connect()
        } finally {
            timer.cancel()
        }
    }

    @Suppress("PrivateApi")
    private fun channelSocket(device: BluetoothDevice, insecure: Boolean): BluetoothSocket {
        val name = if (insecure) "createInsecureRfcommSocket" else "createRfcommSocket"
        val method = device.javaClass.getMethod(name, Int::class.javaPrimitiveType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    suspend fun request(
        method: String,
        params: Any?,
        timeoutMs: Int = 20_000,
    ): RpcResponse = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val out = output ?: throw IOException("Printer is not connected")
            val id = nextId++
            log("→ $method (id $id)")
            writeFrame(out, Frame.json(requestJson(id, method, params)))

            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                readError?.let {
                    throw IOException("Bluetooth read failed: ${it.message}", it)
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    throw IOException("Timed out waiting for '$method'")
                }
                val frame = incoming.poll(
                    minOf(remaining, 250L),
                    TimeUnit.MILLISECONDS,
                ) ?: continue
                val response = parseResponse(frame)
                if (response != null && response.id == id) {
                    if (response.error != null) {
                        throw IOException(response.errorText() ?: "Printer returned an error")
                    }
                    log("← $method ok (id $id)")
                    return@withLock response
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IOException("Unreachable")
        }
    }

    suspend fun upload(
        frames: List<Frame>,
        frameDelayMs: Long = 20,
        onProgress: (sent: Int, total: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val out = output ?: throw IOException("Printer is not connected")
            frames.forEachIndexed { index, frame ->
                writeFrame(out, frame)
                onProgress(index + 1, frames.size)
                if (frameDelayMs > 0) delay(frameDelayMs)
            }
            runCatching { out.flush() }
            log("Uploaded ${frames.size} frames")
        }
    }

    private fun readLoop(stream: InputStream) {
        val buffer = ByteArray(4096)
        try {
            while (reading) {
                val read = stream.read(buffer)
                if (read < 0) break
                synchronized(readerLock) {
                    reader.push(buffer.copyOf(read))
                    while (true) {
                        val frame = reader.nextFrame() ?: break
                        incoming.offer(frame)
                    }
                }
            }
            if (reading) readError = IOException("Printer closed the connection")
        } catch (e: Exception) {
            if (reading) {
                readError = if (e is IOException) e else IOException("Bluetooth read failed", e)
            }
        } finally {
            reading = false
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) { closeNow() }

    private fun closeNow() {
        reading = false
        readThread?.interrupt()
        readThread = null
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
        reader = FrameReader()
        incoming.clear()
        readError = null
    }

    private fun writeFrame(out: OutputStream, frame: Frame) {
        val bytes = frame.encode()
        out.write(bytes)
        out.flush()
    }

    private fun matches(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.contains("liene") ||
            lower.contains("pearl") ||
            lower.contains("hannto")
    }
}

package io.github.bydavy.openliene.printer

data class ConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val name: String? = null,
    val address: String? = null,
)

data class JobProgress(
    val jobId: Int,
    val totalFrames: Int,
    val state: Int? = null,
    val reason: Long? = null,
    val transferSize: Long = 0,
    val fileSize: Long = 0,
    val page: Long = 0,
    val printTimeMs: Long = 0,
)

data class PrinterUiState(
    val connection: ConnectionState = ConnectionState(),
    val status: PrinterStatus? = null,
    val info: DeviceInfo? = null,
    val job: JobProgress? = null,
    val uploadSent: Int = 0,
    val uploadTotal: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val log: List<String> = emptyList(),
)

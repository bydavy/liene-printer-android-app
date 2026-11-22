package io.github.bydavy.liene.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"

/** Bytes of file payload per data frame (the 4-byte job id is added on top). */
const val DATA_CHUNK_SIZE = 892

const val MEDIA_SIZE_2X3 = 5011
const val MEDIA_TYPE_PHOTO = 2010
const val CHANNEL_BLUETOOTH = 14864
const val DOCUMENT_FORMAT_JPEG = 9
const val JOB_TYPE_PRINT = 0

object Methods {
    const val GET_PROP = "get-prop"
    const val PRINT_JOB = "print-job"
    const val CANCEL_JOB = "cancel-job"
    const val GET_JOB_INFO = "get-job-info"
    const val RESUME_PRINTER = "resume-printer"
    const val JOB_ID_LIST = "job-id-list"
}

val STATUS_PROPS = listOf(
    "printer-state",
    "printer-sub-state",
    "printer-state-alerts",
    "media-size",
    "battery",
)

val INFO_PROPS = listOf(
    "model",
    "mac-address",
    "serial-number",
    "sn-pcba",
    "firmware-revision",
    "hardware-revision",
    "media-size",
    "auto-off-interval",
)

fun requestJson(id: Int, method: String, params: Any?): ByteArray {
    val request = JSONObject()
        .put("id", id)
        .put("method", method)
    if (params != null) request.put("params", params)
    request.put("spec-type", 1)
    return request.toString().toByteArray(StandardCharsets.UTF_8)
}

fun printJobParams(
    copies: Int,
    documentName: String,
    fileSize: Long,
): JSONObject = JSONObject()
    .put("copies", copies)
    .put("document-name", documentName)
    .put("file-size", fileSize)
    .put("media-size", MEDIA_SIZE_2X3)
    .put("media-type", MEDIA_TYPE_PHOTO)
    .put("channel", CHANNEL_BLUETOOTH)
    .put("document-format", DOCUMENT_FORMAT_JPEG)
    .put("job-type", JOB_TYPE_PRINT)

fun getPropParams(props: List<String>): JSONArray = JSONArray(props)

fun jobInfoParams(jobId: Int): JSONObject = JSONObject().put("job-id", jobId)

fun cancelJobParams(jobId: Int): JSONArray = JSONArray().put(jobId)

fun frameCount(fileSize: Int): Int = (fileSize + DATA_CHUNK_SIZE - 1) / DATA_CHUNK_SIZE

/**
 * Split [data] into the framed, job-id-prefixed chunks the printer expects.
 *
 * `msgSn` is the 1-based package number, `pkgTotal` is the total package
 * count, and each body begins with the 4-byte little-endian job id.
 */
fun dataFrames(jobId: Int, data: ByteArray): List<Frame> {
    require(data.isNotEmpty()) { "cannot upload an empty file" }
    val total = frameCount(data.size)
    require(total <= 0xFFFF) { "file needs $total frames; maximum is 65535" }

    val frames = ArrayList<Frame>(total)
    var offset = 0
    var index = 1
    while (offset < data.size) {
        val length = minOf(DATA_CHUNK_SIZE, data.size - offset)
        val body = ByteArray(4 + length)
        putLe32(body, 0, jobId.toLong())
        data.copyInto(body, 4, offset, offset + length)
        frames += Frame.data(
            body = body,
            msgSn = index.toLong(),
            pkgTotal = total,
            pkgNum = index,
        )
        offset += length
        index += 1
    }
    return frames
}

data class RpcResponse(
    val id: Int,
    val result: Any?,
    val error: Any?,
) {
    fun jobId(): Int? {
        val value = result ?: return null
        if (value is Number) return value.toInt()
        if (value !is JSONObject) return null
        for (key in listOf("job-id", "job_id", "jobId")) {
            if (value.has(key)) {
                val id = value.optInt(key, -1)
                if (id > 0) return id
            }
        }
        return null
    }

    fun errorText(): String? {
        val value = error ?: return null
        if (value is JSONObject) {
            return value.optString("message").ifBlank { value.toString() }
        }
        return value.toString()
    }
}

/** Returns null for event frames and other non-response traffic. */
fun parseResponse(frame: Frame): RpcResponse? {
    val text = String(frame.body, StandardCharsets.UTF_8)
    val objectValue = runCatching { JSONObject(text) }.getOrNull() ?: return null
    if (objectValue.optString("method").startsWith("event.")) return null
    if (!objectValue.has("id")) return null
    return RpcResponse(
        id = objectValue.optInt("id"),
        result = objectValue.opt("result"),
        error = objectValue.opt("error"),
    )
}

data class JobInfo(
    val jobId: Int,
    val state: Int?,
    val reason: Long?,
    val transferSize: Long,
    val fileSize: Long,
    val page: Long,
    val printTimeMs: Long,
) {
    val isFinished: Boolean
        get() = state != null && state in 7..9
}

fun parseJobInfo(result: Any?): JobInfo? {
    val value = when (result) {
        is JSONArray -> result.optJSONObject(0)
        is JSONObject -> result
        else -> null
    } ?: return null

    val jobId = value.optInt("job-id", value.optInt("job_id", -1))
    if (jobId <= 0) return null
    return JobInfo(
        jobId = jobId,
        state = if (value.has("job-state")) value.optInt("job-state") else null,
        reason = if (value.has("job-state-reason")) value.optLong("job-state-reason") else null,
        transferSize = value.optLong("transfer-size", 0),
        fileSize = value.optLong("file-size", 0),
        page = value.optLong("printing-page-number", 0),
        printTimeMs = value.optLong("print-time", 0),
    )
}

package io.github.bydavy.openliene.protocol

/** Hannto/Liene framed protocol used by the printer over Bluetooth Classic SPP. */
const val FRAME_DELIMITER = 0x7E
const val FRAME_VERSION = 100
const val HEADER_LEN = 20
const val OVERHEAD = HEADER_LEN + 2
const val MAX_BODY_LEN = 0x03FF

const val CONNECT_MESSAGE = 1
const val CONNECT_DATA = 2
const val INTERACTIVE_REQUEST = 6
const val ENCODING_JSON = 3
const val ENCODING_BINARY = 2

private const val ATTR_LEN_MASK = 0x03FF
private const val ATTR_ENCRYPT_SHIFT = 10
private const val ATTR_ENCRYPT_MASK = 0x1C00
private const val ATTR_SPLIT = 0x2000

/**
 * A single frame in the Hannto protocol.
 *
 * A control frame is `[0x7E][20-byte header][body][checksum][0x7E]`. Data
 * frames use the same envelope but carry a 4-byte little-endian job id
 * followed by a 892-byte file chunk.
 */
data class Frame(
    val body: ByteArray,
    val version: Int = FRAME_VERSION,
    val reserve: Int = 0,
    val connectType: Int = CONNECT_MESSAGE,
    val interactive: Int = INTERACTIVE_REQUEST,
    val encoding: Int = ENCODING_JSON,
    val terminalId: Long = 0,
    val msgSn: Long = 0,
    val pkgTotal: Int = 0,
    val pkgNum: Int = 0,
    val encryptType: Int = 0,
    val split: Boolean = false,
) {
    fun encode(): ByteArray {
        require(body.size <= MAX_BODY_LEN) { "body length ${body.size} exceeds 10-bit field" }
        require(encryptType in 0..6) { "unsupported encryption type $encryptType" }
        require(version in 0..255 && reserve in 0..255) { "header byte out of range" }
        require(connectType in 0..255 && interactive in 0..255 && encoding in 0..255)
        require(msgSn in 0..0xFFFF_FFFFL && terminalId in 0..0xFFFF_FFFFL)
        require(pkgTotal in 0..0xFFFF && pkgNum in 0..0xFFFF)

        val out = ByteArray(body.size + OVERHEAD)
        out[0] = FRAME_DELIMITER.toByte()
        out[1] = version.toByte()
        out[2] = reserve.toByte()
        out[3] = connectType.toByte()
        out[4] = interactive.toByte()
        out[5] = encoding.toByte()
        putLe32(out, 6, terminalId)
        putLe32(out, 10, msgSn)
        putLe16(out, 14, pkgTotal)
        putLe16(out, 16, pkgNum)

        var attributes = body.size and ATTR_LEN_MASK
        attributes = attributes or ((encryptType shl ATTR_ENCRYPT_SHIFT) and ATTR_ENCRYPT_MASK)
        if (split) attributes = attributes or ATTR_SPLIT
        putLe16(out, 18, attributes)

        body.copyInto(out, HEADER_LEN)
        out[HEADER_LEN + body.size] = checksum(out, 1, HEADER_LEN + body.size).toByte()
        out[out.lastIndex] = FRAME_DELIMITER.toByte()
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return body.contentEquals(other.body) &&
            version == other.version &&
            reserve == other.reserve &&
            connectType == other.connectType &&
            interactive == other.interactive &&
            encoding == other.encoding &&
            terminalId == other.terminalId &&
            msgSn == other.msgSn &&
            pkgTotal == other.pkgTotal &&
            pkgNum == other.pkgNum &&
            encryptType == other.encryptType &&
            split == other.split
    }

    override fun hashCode(): Int = body.contentHashCode() * 31 + msgSn.hashCode()

    companion object {
        fun json(body: ByteArray): Frame = Frame(body)

        fun data(body: ByteArray, msgSn: Long, pkgTotal: Int, pkgNum: Int): Frame =
            Frame(
                body = body,
                connectType = CONNECT_DATA,
                encoding = ENCODING_BINARY,
                msgSn = msgSn,
                pkgTotal = pkgTotal,
                pkgNum = pkgNum,
            )

        fun decode(bytes: ByteArray): Frame {
            require(bytes.size >= OVERHEAD) { "frame too short" }
            require(unsigned(bytes[0]) == FRAME_DELIMITER) { "missing start delimiter" }
            require(unsigned(bytes[bytes.lastIndex]) == FRAME_DELIMITER) { "missing end delimiter" }

            val attributes = le16(bytes, 18)
            val bodyLen = attributes and ATTR_LEN_MASK
            require(bytes.size == bodyLen + OVERHEAD) {
                "length mismatch: header says $bodyLen, frame has ${bytes.size - OVERHEAD}"
            }

            val expected = checksum(bytes, 1, HEADER_LEN + bodyLen)
            val found = unsigned(bytes[HEADER_LEN + bodyLen])
            require(expected == found) {
                "bad checksum: expected ${expected.toString(16)}, found ${found.toString(16)}"
            }

            return Frame(
                body = bytes.copyOfRange(HEADER_LEN, HEADER_LEN + bodyLen),
                version = unsigned(bytes[1]),
                reserve = unsigned(bytes[2]),
                connectType = unsigned(bytes[3]),
                interactive = unsigned(bytes[4]),
                encoding = unsigned(bytes[5]),
                terminalId = le32(bytes, 6),
                msgSn = le32(bytes, 10),
                pkgTotal = le16(bytes, 14),
                pkgNum = le16(bytes, 16),
                encryptType = (attributes and ATTR_ENCRYPT_MASK) ushr ATTR_ENCRYPT_SHIFT,
                split = attributes and ATTR_SPLIT != 0,
            )
        }
    }
}

internal fun unsigned(value: Byte): Int = value.toInt() and 0xFF

internal fun putLe16(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xFF).toByte()
    out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

internal fun putLe32(out: ByteArray, offset: Int, value: Long) {
    out[offset] = (value and 0xFF).toByte()
    out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}

internal fun le16(bytes: ByteArray, offset: Int): Int =
    unsigned(bytes[offset]) or (unsigned(bytes[offset + 1]) shl 8)

internal fun le32(bytes: ByteArray, offset: Int): Long =
    unsigned(bytes[offset]).toLong() or
        (unsigned(bytes[offset + 1]).toLong() shl 8) or
        (unsigned(bytes[offset + 2]).toLong() shl 16) or
        (unsigned(bytes[offset + 3]).toLong() shl 24)

private fun checksum(bytes: ByteArray, from: Int, until: Int): Int {
    var sum = 0
    for (i in from until until) sum += unsigned(bytes[i])
    return sum and 0xFF
}

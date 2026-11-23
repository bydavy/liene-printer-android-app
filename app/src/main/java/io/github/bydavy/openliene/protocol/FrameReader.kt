package io.github.bydavy.openliene.protocol

/**
 * Incremental frame reader for the printer's byte stream.
 *
 * The reader resynchronises by skipping bytes when it sees a bad delimiter or
 * checksum, so unsolicited/log output cannot permanently wedge the stream.
 */
class FrameReader {
    private var buffer = ByteArray(0)

    fun push(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val next = ByteArray(buffer.size + bytes.size)
        buffer.copyInto(next)
        bytes.copyInto(next, buffer.size)
        buffer = next
    }

    fun nextFrame(): Frame? {
        while (true) {
            val start = findStart()
            if (start == null) {
                // Keep the last two bytes: they may be the beginning of a
                // frame whose 3-byte header has not arrived yet.
                if (buffer.size > 2) {
                    buffer = buffer.copyOfRange(buffer.size - 2, buffer.size)
                }
                return null
            }
            if (start > 0) {
                buffer = buffer.copyOfRange(start, buffer.size)
            }
            if (buffer.size < OVERHEAD) return null

            val bodyLen = le16(buffer, 18) and MAX_BODY_LEN
            val total = bodyLen + OVERHEAD
            if (buffer.size < total) return null

            if (unsigned(buffer[total - 1]) != FRAME_DELIMITER) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            val candidate = buffer.copyOfRange(0, total)
            try {
                val frame = Frame.decode(candidate)
                buffer = buffer.copyOfRange(total, buffer.size)
                return frame
            } catch (_: IllegalArgumentException) {
                buffer = buffer.copyOfRange(1, buffer.size)
            }
        }
    }

    private fun findStart(): Int? {
        if (buffer.size < 3) return null
        for (i in 0..buffer.size - 3) {
            if (unsigned(buffer[i]) == FRAME_DELIMITER &&
                unsigned(buffer[i + 1]) in intArrayOf(FRAME_VERSION, 1) &&
                unsigned(buffer[i + 2]) == 0
            ) {
                return i
            }
        }
        return null
    }
}

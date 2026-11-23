package io.github.bydavy.openliene.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTest {
    @Test
    fun jsonFrameMatchesKnownLayout() {
        val body = """{"id":1}""".toByteArray()
        val frame = Frame.json(body)
        val bytes = frame.encode()

        assertEquals(0x7E, unsigned(bytes[0]))
        assertEquals(100, unsigned(bytes[1]))
        assertEquals(0, unsigned(bytes[2]))
        assertEquals(1, unsigned(bytes[3]))
        assertEquals(6, unsigned(bytes[4]))
        assertEquals(3, unsigned(bytes[5]))
        assertEquals(8, le16(bytes, 18))
        assertArrayEquals(body, bytes.copyOfRange(HEADER_LEN, HEADER_LEN + 8))
        assertEquals(0x7E, unsigned(bytes.last()))
        assertEquals(8 + OVERHEAD, bytes.size)

        val decoded = Frame.decode(bytes)
        assertEquals(frame.connectType, decoded.connectType)
        assertArrayEquals(body, decoded.body)
    }

    @Test
    fun dataFrameHasJobOrderingFields() {
        val frame = Frame.data(ByteArray(896) { 0xAA.toByte() }, 7, 12, 3)
        val bytes = frame.encode()

        assertEquals(2, unsigned(bytes[3]))
        assertEquals(2, unsigned(bytes[5]))
        assertEquals(7L, le32(bytes, 10))
        assertEquals(12, le16(bytes, 14))
        assertEquals(3, le16(bytes, 16))
        assertEquals(896, le16(bytes, 18))
        assertEquals(896 + OVERHEAD, bytes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedBody() {
        Frame.json(ByteArray(MAX_BODY_LEN + 1)).encode()
    }

    @Test
    fun readerHandlesFragmentationAndGarbage() {
        val a = Frame.json("""{"id":1,"result":{}}""".toByteArray()).encode()
        val b = Frame.json("""{"id":2}""".toByteArray()).encode()
        val reader = FrameReader()

        reader.push(byteArrayOf(0x00, 0x11, 0x22))
        reader.push(a.copyOfRange(0, 5))
        assertNull(reader.nextFrame())
        reader.push(a.copyOfRange(5, a.size))

        assertEquals("""{"id":1,"result":{}}""", String(reader.nextFrame()!!.body))
        reader.push(b)
        assertEquals("""{"id":2}""", String(reader.nextFrame()!!.body))
        assertNull(reader.nextFrame())
    }

    @Test
    fun readerResyncsAfterBadChecksum() {
        val good = Frame.json("""{"id":9}""".toByteArray()).encode()
        val corrupted = good.copyOf()
        corrupted[corrupted.lastIndex - 1] = (unsigned(corrupted[corrupted.lastIndex - 1]) xor 0xFF).toByte()

        val reader = FrameReader()
        reader.push(corrupted)
        assertNull(reader.nextFrame())
        reader.push(good)
        assertEquals("""{"id":9}""", String(reader.nextFrame()!!.body))
    }

    @Test
    fun checksumCoversHeaderAndBody() {
        val frame = Frame.json("hello".toByteArray())
        val bytes = frame.encode()
        val checksum = bytes[bytes.lastIndex - 1]
        var sum = 0
        for (i in 1 until bytes.lastIndex - 1) sum += unsigned(bytes[i])
        assertEquals(sum.toByte(), checksum)
        assertTrue(bytes.size > OVERHEAD)
    }
}

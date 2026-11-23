package io.github.bydavy.openliene.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun printJobRequestMatchesAppShape() {
        val json = JSONObject(
            String(requestJson(42, Methods.PRINT_JOB, printJobParams(1, "photo.jpg", 123_456))),
        )
        assertEquals(42, json.getInt("id"))
        assertEquals("print-job", json.getString("method"))
        assertEquals(1, json.getInt("spec-type"))
        val params = json.getJSONObject("params")
        assertEquals(1, params.getInt("copies"))
        assertEquals("photo.jpg", params.getString("document-name"))
        assertEquals(123_456L, params.getLong("file-size"))
        assertEquals(5011, params.getInt("media-size"))
        assertEquals(2010, params.getInt("media-type"))
        assertEquals(14864, params.getInt("channel"))
        assertEquals(9, params.getInt("document-format"))
        assertEquals(0, params.getInt("job-type"))
    }

    @Test
    fun parsesBothJobIdSpellings() {
        val a = parseResponse(Frame.json("""{"id":1,"result":{"job-id":7}}""".toByteArray()))
        val b = parseResponse(Frame.json("""{"id":1,"result":{"job_id":8}}""".toByteArray()))
        assertEquals(7, a!!.jobId()!!)
        assertEquals(8, b!!.jobId()!!)
    }

    @Test
    fun parsesErrorResponse() {
        val response = parseResponse(
            Frame.json("""{"id":3,"error":{"code":-5001,"message":"unsupported"}}""".toByteArray()),
        )!!
        assertEquals(3, response.id)
        assertEquals("unsupported", response.errorText())
    }

    @Test
    fun ignoresEventFrames() {
        val event = parseResponse(
            Frame.json("""{"method":"event.state","params":{}}""".toByteArray()),
        )
        assertNull(event)
    }

    @Test
    fun splitsInto892ByteChunksWithJobId() {
        val data = ByteArray(2000) { 0xAB.toByte() }
        val frames = dataFrames(0x11223344, data)
        assertEquals(3, frames.size)
        assertEquals(4 + 892, frames[0].body.size)
        assertEquals(4 + 892, frames[1].body.size)
        assertEquals(4 + 216, frames[2].body.size)

        frames.forEachIndexed { index, frame ->
            assertEquals(3, frame.pkgTotal)
            assertEquals(index + 1, frame.pkgNum)
            assertEquals((index + 1).toLong(), frame.msgSn)
            assertEquals(CONNECT_DATA, frame.connectType)
            assertEquals(INTERACTIVE_REQUEST, frame.interactive)
            assertEquals(ENCODING_BINARY, frame.encoding)
            assertEquals(0x11223344, le32(frame.body, 0).toInt())
        }

        val payload = frames.flatMap { it.body.drop(4) }.toByteArray()
        assertTrue(payload.contentEquals(data))
    }

    @Test
    fun exactBoundaryHasNoEmptyTail() {
        val frames = dataFrames(1, ByteArray(DATA_CHUNK_SIZE * 2))
        assertEquals(2, frames.size)
        assertEquals(4 + DATA_CHUNK_SIZE, frames[1].body.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyFile() {
        dataFrames(1, ByteArray(0))
    }

    @Test
    fun parsesJobInfoArray() {
        val response = parseResponse(
            Frame.json(
                """{"id":4,"result":[{"job-id":20,"job-state":3,"transfer-size":100,"file-size":200}]}"""
                    .toByteArray(),
            ),
        )!!
        val info = parseJobInfo(response.result)!!
        assertEquals(20, info.jobId)
        assertEquals(3, info.state)
        assertEquals(100L, info.transferSize)
        assertEquals(200L, info.fileSize)
    }
}

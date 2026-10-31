package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class H264AccessUnitsTest {
    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1F)
    private val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x38, 0x80.toByte())
    private val idr = byteArrayOf(0, 0, 0, 1, 0x65, 0x88.toByte(), 0x11, 0x22)
    private val sliceA1 = byteArrayOf(0, 0, 1, 0x41, 0x9A.toByte(), 0x01)
    private val sliceA2 = byteArrayOf(0, 0, 1, 0x41, 0x40, 0x02) // tranche suivante de la même image
    private val sliceB = byteArrayOf(0, 0, 0, 1, 0x41, 0x9A.toByte(), 0x03)

    private fun run(stream: ByteArray, chunk: Int): List<Pair<ByteArray, Boolean>> {
        val out = mutableListOf<Pair<ByteArray, Boolean>>()
        val splitter = H264AccessUnits { data, config -> out += data to config }
        var i = 0
        while (i < stream.size) {
            val n = minOf(chunk, stream.size - i)
            splitter.feed(stream, i, n)
            i += n
        }
        splitter.flush()
        return out
    }

    @Test
    fun splitsConfigAndPictures_whateverTheChunkSize() {
        val stream = sps + pps + idr + sliceA1 + sliceA2 + sliceB
        for (chunk in listOf(1, 2, 3, 5, 7, 1000)) {
            val out = run(stream, chunk)
            assertEquals("découpage par $chunk octets", 5, out.size)
            assertArrayEquals(sps, out[0].first)
            assertEquals(true, out[0].second)
            assertArrayEquals(pps, out[1].first)
            assertArrayEquals(idr, out[2].first)
            assertEquals(false, out[2].second)
            assertArrayEquals(sliceA1 + sliceA2, out[3].first)
            assertArrayEquals(sliceB, out[4].first)
        }
    }

    @Test
    fun ignoresGarbageBeforeTheFirstStartCode() {
        val out = run(byteArrayOf(9, 9, 9) + sps + idr, 2)
        assertEquals(2, out.size)
        assertArrayEquals(sps, out[0].first)
        assertArrayEquals(idr, out[1].first)
    }
}

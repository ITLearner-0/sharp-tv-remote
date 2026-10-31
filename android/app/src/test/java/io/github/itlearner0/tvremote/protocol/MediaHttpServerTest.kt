package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class MediaHttpServerTest {
    private val data = ByteArray(200_000) { (it % 251).toByte() }

    private val source = object : MediaSource {
        override val name = "vacances été.mp4"
        override val mimeType = "video/mp4"
        override val size = data.size.toLong()
        override fun open(offset: Long) = data.inputStream().also { it.skip(offset) }
    }

    @Test
    fun servesWholeFileAndRanges() {
        MediaHttpServer().use { server ->
            val url = server.publish("127.0.0.1", 0, source)
            val full = URL(url).openConnection() as HttpURLConnection
            assertEquals(200, full.responseCode)
            assertEquals("video/mp4", full.contentType)
            assertArrayEquals(data, full.inputStream.readBytes())

            val part = URL(url).openConnection() as HttpURLConnection
            part.setRequestProperty("Range", "bytes=1000-1999")
            assertEquals(206, part.responseCode)
            assertEquals("bytes 1000-1999/200000", part.getHeaderField("Content-Range"))
            assertArrayEquals(data.copyOfRange(1000, 2000), part.inputStream.readBytes())

            val wrong = URL(url.replace(Regex("/[0-9a-f]{24}/"), "/0000/")).openConnection() as HttpURLConnection
            assertEquals(404, wrong.responseCode)
        }
    }

    @Test
    fun parsesRanges() {
        assertEquals(0L..99L, MediaHttpServer.parseRange("bytes=0-99", 1000))
        assertEquals(900L..999L, MediaHttpServer.parseRange("bytes=900-", 1000))
        assertEquals(950L..999L, MediaHttpServer.parseRange("bytes=-50", 1000))
        assertEquals(10L..999L, MediaHttpServer.parseRange("bytes=10-5000", 1000))
        assertNull(MediaHttpServer.parseRange("bytes=2000-", 1000))
    }
}

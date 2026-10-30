package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey

class ProtocolTest {

    @Test
    fun protobufRoundTrip() {
        val bytes = ProtoWriter()
            .int(1, -5)
            .string(2, "é")
            .message(3) { bool(1, true) }
            .int(4, 300)
            .toByteArray()
        val message = ProtoMessage(bytes)
        assertEquals(-5, message.int(1))
        assertEquals("é", message.string(2))
        assertTrue(message.message(3)!!.bool(1))
        assertEquals(300, message.int(4))
    }

    @Test
    fun delimitedFraming() {
        val out = ByteArrayOutputStream()
        val big = ByteArray(1000) { it.toByte() }
        writeDelimited(out, byteArrayOf(1, 2, 3))
        writeDelimited(out, big)
        val input = ByteArrayInputStream(out.toByteArray())
        assertArrayEquals(byteArrayOf(1, 2, 3), readDelimited(input))
        assertArrayEquals(big, readDelimited(input))
    }

    @Test
    fun identityIsPersisted() {
        val dir = Files.createTempDirectory("identity").toFile()
        val first = ClientIdentity.loadOrCreate(dir)
        val second = ClientIdentity.loadOrCreate(dir)
        assertEquals(first.certificate, second.certificate)
        first.certificate.verify(first.certificate.publicKey)
        first.sslContext()
    }

    @Test
    fun pairingSecretMatchesCode() {
        val client = ClientIdentity.generate("client").certificate.publicKey as RSAPublicKey
        val server = ClientIdentity.generate("tv").certificate.publicKey as RSAPublicKey
        // La TV choisit la fin du code ; le début est le premier octet du hash.
        val suffix = "4F2A"
        val hash = PairingSession.computeSecret(client, server, "00$suffix")
        val code = "%02X".format(hash[0].toInt() and 0xFF) + suffix
        assertArrayEquals(hash, PairingSession.computeSecret(client, server, code))
    }

    @Test
    fun wakeOnLanPacket() {
        val packet = WakeOnLan.packet("a4-b1-c2-d3-e4-f5")
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        assertEquals(0xA4.toByte(), packet[6])
        assertEquals("A4:B1:C2:D3:E4:F5", WakeOnLan.normalizeMac("a4b1c2d3e4f5"))
        assertNull(WakeOnLan.normalizeMac("zz"))
    }
}

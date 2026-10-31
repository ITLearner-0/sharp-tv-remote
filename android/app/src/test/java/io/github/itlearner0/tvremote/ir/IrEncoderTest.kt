package io.github.itlearner0.tvremote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie les encodeurs contre des codes de référence publiés partout sous
 * forme hexadécimale (premier bit émis = bit de poids fort de la valeur).
 */
class IrEncoderTest {

    /** Décode une trame à distance d'impulsion (NEC, Samsung…) en valeur hexadécimale. */
    private fun pulseDistanceHex(signal: IrSignal, bits: Int): String {
        val p = signal.pattern
        var value = 0L
        for (i in 0 until bits) {
            val space = p[3 + 2 * i] // [0]=en-tête, [1]=pause d'en-tête, puis (impulsion, pause)…
            value = (value shl 1) or (if (space > 1_000) 1L else 0L)
        }
        return "%0${bits / 4}X".format(value)
    }

    private fun encode(protocol: String, address: Int, command: Int) =
        IrEncoder.encode(IrCode.Parsed(protocol, address, command))

    @Test
    fun samsungPowerIsE0E040BF() {
        val signal = encode("Samsung32", 0x07, 0x02)
        assertEquals(38_000, signal.frequency)
        assertEquals(4_500, signal.pattern[0])
        assertEquals("E0E040BF", pulseDistanceHex(signal, 32))
        assertEquals("E0E0E01F", pulseDistanceHex(encode("Samsung32", 0x07, 0x07), 32)) // volume +
    }

    @Test
    fun lgPowerIs20DF10EF() {
        val signal = encode("NEC", 0x04, 0x08)
        assertEquals(9_000, signal.pattern[0])
        assertEquals("20DF10EF", pulseDistanceHex(signal, 32))
        assertEquals("20DF40BF", pulseDistanceHex(encode("NEC", 0x04, 0x02), 32)) // volume +
    }

    @Test
    fun necExtendedSendsBothAddressBytes() {
        // Continental Edison : adresse 00 7F, commande 15 EA (fichier Flipper).
        assertEquals("00FEA857", pulseDistanceHex(encode("NECext", 0x7F00, 0xEA15), 32))
    }

    @Test
    fun sonyPowerIsA90SentThreeTimes() {
        val signal = encode("SIRC", 0x01, 0x15)
        assertEquals(40_000, signal.frequency)
        val frame = signal.pattern.size / 3
        var value = 0
        for (i in 0 until 12) {
            val mark = signal.pattern[2 + 2 * i]
            value = (value shl 1) or (if (mark > 900) 1 else 0)
        }
        assertEquals("A90", "%03X".format(value))
        assertEquals(26, frame) // en-tête (2) + 12 bits × 2
        assertEquals(45_000, signal.pattern.copyOfRange(0, frame).sum())
    }

    @Test
    fun rc5And6AreWellFormed() {
        for (protocol in listOf("RC5", "RC6")) {
            for (toggle in listOf(false, true)) {
                val signal = IrEncoder.encode(IrCode.Parsed(protocol, 0, 12), toggle)
                assertEquals(36_000, signal.frequency)
                assertTrue(signal.pattern.all { it > 0 })
            }
        }
        // RC5 : 14 demi-bits de 889 µs × 2 = 24 892 µs d'émission + pause finale.
        val rc5 = IrEncoder.encode(IrCode.Parsed("RC5", 0, 12))
        assertEquals(14 * 2 * 889, rc5.pattern.sum() - 90_000 + 889)
    }

    @Test
    fun panasonicHasChecksum() {
        val signal = encode("Panasonic", 128, 61) // Marche
        assertEquals(3_456, signal.pattern[0])
        var bytes = IntArray(6)
        for (i in 0 until 48) {
            val bit = if (signal.pattern[3 + 2 * i] > 800) 1 else 0
            bytes[i / 8] = bytes[i / 8] or (bit shl (i % 8))
        }
        assertEquals(listOf(0x02, 0x20, 0x80, 0x00, 0x3D, 0xBD), bytes.toList())
    }

    @Test
    fun sharpSendsInvertedSecondFrame() {
        val signal = encode("Sharp", 1, 22)
        fun frameBits(offset: Int) = (0 until 15).map { if (signal.pattern[offset + 1 + 2 * it] > 1_000) 1 else 0 }
        val first = frameBits(0)
        val second = frameBits(32)
        assertEquals(listOf(1, 0, 0, 0, 0), first.take(5))
        assertEquals(first.subList(5, 13).map { 1 - it }, second.subList(5, 13))
        assertEquals(listOf(1, 0), first.takeLast(2))
        assertEquals(listOf(0, 1), second.takeLast(2))
    }
}

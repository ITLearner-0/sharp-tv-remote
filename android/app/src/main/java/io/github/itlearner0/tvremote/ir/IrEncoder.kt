package io.github.itlearner0.tvremote.ir

/** Code infrarouge : protocole + adresse + commande, ou signal brut. */
sealed interface IrCode {
    /** Convention Flipper Zero : octets envoyés bit de poids faible en premier. */
    data class Parsed(val protocol: String, val address: Int, val command: Int) : IrCode

    /** Durées en microsecondes, en alternance émission / pause, en commençant par une émission. */
    class Raw(val frequency: Int, val timings: IntArray) : IrCode
}

/** Signal prêt à émettre : fréquence porteuse (Hz) et durées alternées émission / pause (µs). */
class IrSignal(val frequency: Int, val pattern: IntArray)

/** Transforme un code infrarouge en signal, pour les protocoles TV courants. */
object IrEncoder {
    val SUPPORTED = setOf("NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC15", "SIRC20", "Panasonic", "Sharp")

    /** [toggle] : bit de bascule RC5/RC6, à inverser à chaque nouvel appui. */
    fun encode(code: IrCode, toggle: Boolean = false): IrSignal = when (code) {
        is IrCode.Raw -> IrSignal(code.frequency, code.timings.filter { it > 0 }.toIntArray())
        is IrCode.Parsed -> encodeParsed(code, toggle)
    }

    private fun encodeParsed(code: IrCode.Parsed, toggle: Boolean): IrSignal {
        val a = code.address
        val c = code.command
        return when (code.protocol) {
            "NEC" -> pulseDistance(38_000, 9_000, 4_500, bytes(a, a.inv(), c, c.inv()), trailingGap = 40_000)
            "NECext" -> pulseDistance(38_000, 9_000, 4_500, bytes(a, a shr 8, c, c shr 8), trailingGap = 40_000)
            "Samsung32" -> pulseDistance(38_000, 4_500, 4_500, bytes(a, a, c, c.inv()), trailingGap = 46_000)
            "RC5" -> rc5(a, c, extended = false, toggle)
            "RC5X" -> rc5(a, c, extended = true, toggle)
            "RC6" -> rc6(a, c, toggle)
            "SIRC" -> sirc(c, a and 0x1F, 5)
            "SIRC15" -> sirc(c, a and 0xFF, 8)
            "SIRC20" -> sirc(c, a and 0x1FFF, 13)
            "Panasonic" -> panasonic(device = a and 0xFF, subdevice = a shr 8 and 0xFF, function = c and 0xFF)
            "Sharp" -> sharp(a and 0x1F, c and 0xFF)
            else -> throw IllegalArgumentException("Protocole non pris en charge : ${code.protocol}")
        }
    }

    private fun bytes(vararg values: Int): IntArray = values.map { it and 0xFF }.toIntArray()

    /** NEC, Samsung… : chaque bit est une impulsion suivie d'une pause courte (0) ou longue (1). */
    private fun pulseDistance(
        frequency: Int,
        leadMark: Int,
        leadSpace: Int,
        data: IntArray,
        bitMark: Int = 560,
        zeroSpace: Int = 560,
        oneSpace: Int = 1_690,
        trailingGap: Int,
    ): IrSignal {
        val p = Pattern().mark(leadMark).space(leadSpace)
        for (byte in data) {
            for (bit in 0 until 8) p.mark(bitMark).space(if (byte shr bit and 1 == 1) oneSpace else zeroSpace)
        }
        p.mark(bitMark).space(trailingGap)
        return IrSignal(frequency, p.build())
    }

    /** RC5 (Philips) : codage Manchester, 889 µs par demi-bit, bits de poids fort en premier. */
    private fun rc5(address: Int, command: Int, extended: Boolean, toggle: Boolean): IrSignal {
        val t = 889
        val bits = ArrayList<Int>()
        bits += 1
        bits += if (extended) (command shr 6 and 1) xor 1 else 1
        bits += if (toggle) 1 else 0
        for (i in 4 downTo 0) bits += address shr i and 1
        for (i in 5 downTo 0) bits += command shr i and 1
        val p = Pattern()
        for (bit in bits) if (bit == 1) p.space(t).mark(t) else p.mark(t).space(t)
        p.space(90_000)
        return IrSignal(36_000, p.build())
    }

    /** RC6 mode 0 (Philips récents) : en-tête, bit de départ, mode, bascule double, 16 bits. */
    private fun rc6(address: Int, command: Int, toggle: Boolean): IrSignal {
        val t = 444
        val p = Pattern().mark(6 * t).space(2 * t)
        fun bit(value: Int, width: Int = t) {
            if (value == 1) p.mark(width).space(width) else p.space(width).mark(width)
        }
        bit(1) // départ
        repeat(3) { bit(0) } // mode 0
        bit(if (toggle) 1 else 0, 2 * t)
        for (i in 7 downTo 0) bit(address shr i and 1)
        for (i in 7 downTo 0) bit(command shr i and 1)
        p.space(90_000)
        return IrSignal(36_000, p.build())
    }

    /** SIRC (Sony) : 7 bits de commande puis l'adresse, émis 3 fois toutes les 45 ms. */
    private fun sirc(command: Int, address: Int, addressBits: Int): IrSignal {
        val frame = Pattern().mark(2_400).space(600)
        val bits = (0 until 7).map { command shr it and 1 } + (0 until addressBits).map { address shr it and 1 }
        for (bit in bits) frame.mark(if (bit == 1) 1_200 else 600).space(600)
        val one = frame.build()
        val length = one.sum()
        one[one.size - 1] += (45_000 - length).coerceAtLeast(10_000)
        return IrSignal(40_000, one + one + one)
    }

    /** Panasonic (Kaseikyo 48 bits) : fabricant 0x2002, appareil, sous-appareil, fonction, contrôle. */
    private fun panasonic(device: Int, subdevice: Int, function: Int): IrSignal {
        val t = 432
        val data = bytes(0x02, 0x20, device, subdevice, function, device xor subdevice xor function)
        return pulseDistance(37_000, 8 * t, 4 * t, data, bitMark = t, zeroSpace = t, oneSpace = 3 * t, trailingGap = 75_000)
    }

    /** Sharp : 15 bits, puis la même trame avec la commande inversée 40 ms plus tard. */
    private fun sharp(address: Int, command: Int): IrSignal {
        val p = Pattern()
        fun frame(cmd: Int, expansion: Int, check: Int) {
            val bits = (0 until 5).map { address shr it and 1 } + (0 until 8).map { cmd shr it and 1 } + listOf(expansion, check)
            for (bit in bits) p.mark(320).space(if (bit == 1) 1_680 else 680)
            p.mark(320)
        }
        frame(command, 1, 0)
        p.space(40_000)
        frame(command.inv() and 0xFF, 0, 1)
        p.space(40_000)
        return IrSignal(38_000, p.build())
    }

    /** Suite de durées alternées ; deux durées consécutives de même nature sont fusionnées. */
    private class Pattern {
        private val durations = ArrayList<Int>()

        fun mark(us: Int) = add(us, mark = true)

        fun space(us: Int) = add(us, mark = false)

        private fun add(us: Int, mark: Boolean): Pattern {
            if (durations.isEmpty() && !mark) return this // un signal commence toujours par une émission
            val lastIsMark = durations.size % 2 == 1
            if (durations.isNotEmpty() && lastIsMark == mark) {
                durations[durations.size - 1] += us
            } else {
                durations += us
            }
            return this
        }

        fun build(): IntArray = durations.toIntArray()
    }
}

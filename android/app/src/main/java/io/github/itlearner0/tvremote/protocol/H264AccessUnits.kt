package io.github.itlearner0.tvremote.protocol

import java.io.ByteArrayOutputStream

/**
 * Découpe un flux H.264 brut (« Annex B », celui de screenrecord) en images
 * complètes pour le décodeur : les paramètres (SPS, PPS) sont rendus à part,
 * marqués « configuration » ; chaque image regroupe ses tranches.
 */
class H264AccessUnits(private val emit: (data: ByteArray, config: Boolean) -> Unit) {
    private var buffer = ByteArray(1 shl 16)
    private var size = 0
    /** Début (code de départ compris) de l'unité en cours de lecture, ou -1. */
    private var nalStart = -1
    private var scan = 0
    private val unit = ByteArrayOutputStream()
    private var unitHasPicture = false

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        ensure(size + length)
        System.arraycopy(data, offset, buffer, size, length)
        size += length
        var i = maxOf(scan, 2)
        while (i < size) {
            if (buffer[i] == 1.toByte() && buffer[i - 1] == 0.toByte() && buffer[i - 2] == 0.toByte()) {
                val start = if (i >= 3 && buffer[i - 3] == 0.toByte()) i - 3 else i - 2
                if (nalStart >= 0 && start > nalStart) nal(buffer.copyOfRange(nalStart, start))
                nalStart = start
            }
            i++
        }
        scan = size
        // Garde seulement l'unité en cours.
        if (nalStart > 0) {
            System.arraycopy(buffer, nalStart, buffer, 0, size - nalStart)
            size -= nalStart
            scan -= nalStart
            nalStart = 0
        } else if (nalStart < 0) {
            // Pas encore de code de départ : on garde les 3 derniers octets au cas où il serait coupé.
            val keep = minOf(size, 3)
            System.arraycopy(buffer, size - keep, buffer, 0, keep)
            size = keep
            scan = keep
        }
    }

    /** Rend l'image en cours (fin du flux). */
    fun flush() {
        if (nalStart >= 0 && size > nalStart) nal(buffer.copyOfRange(nalStart, size))
        size = 0
        scan = 0
        nalStart = -1
        flushUnit()
    }

    private fun nal(nal: ByteArray) {
        val header = if (nal.size > 3 && nal[2] == 1.toByte()) 3 else 4
        if (nal.size <= header) return
        when (nal[header].toInt() and 0x1F) {
            7, 8 -> {
                flushUnit()
                emit(nal, true)
            }
            9, 6 -> {
                if (unitHasPicture) flushUnit()
                unit.write(nal)
            }
            1, 5 -> {
                // Premier octet de tranche : « first_mb_in_slice == 0 » ⇒ nouvelle image.
                val firstSlice = nal.size > header + 1 && (nal[header + 1].toInt() and 0x80) != 0
                if (firstSlice && unitHasPicture) flushUnit()
                unit.write(nal)
                unitHasPicture = true
            }
            else -> unit.write(nal)
        }
    }

    private fun flushUnit() {
        if (unit.size() > 0 && unitHasPicture) emit(unit.toByteArray(), false)
        unit.reset()
        unitHasPicture = false
    }

    private fun ensure(capacity: Int) {
        if (capacity <= buffer.size) return
        var next = buffer.size
        while (next < capacity) next *= 2
        buffer = buffer.copyOf(next)
    }
}

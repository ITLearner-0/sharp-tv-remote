package io.github.itlearner0.tvremote.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Encodeur / décodeur protobuf minimal.
 *
 * Le protocole Android TV Remote n'utilise que des entiers, des booléens,
 * des chaînes, des octets et des sous-messages : inutile d'embarquer la
 * bibliothèque protobuf complète.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): ProtoWriter = apply {
        tag(field, WIRE_VARINT)
        writeVarint(out, value)
    }

    fun int(field: Int, value: Int): ProtoWriter = int(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = int(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, WIRE_LENGTH)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())
}

/** Message protobuf décodé : valeurs indexées par numéro de champ. */
class ProtoMessage(bytes: ByteArray) {
    private val fields = HashMap<Int, MutableList<Any>>()

    init {
        var pos = 0
        while (pos < bytes.size) {
            val (key, afterKey) = readVarint(bytes, pos)
            val field = (key ushr 3).toInt()
            pos = afterKey
            when ((key and 7).toInt()) {
                WIRE_VARINT -> {
                    val (value, next) = readVarint(bytes, pos)
                    add(field, value)
                    pos = next
                }
                WIRE_FIXED64 -> pos += 8
                WIRE_LENGTH -> {
                    val (length, next) = readVarint(bytes, pos)
                    val end = next + length.toInt()
                    if (length < 0 || end > bytes.size) throw ProtocolException("Message tronqué")
                    add(field, bytes.copyOfRange(next, end))
                    pos = end
                }
                WIRE_FIXED32 -> pos += 4
                else -> throw ProtocolException("Type protobuf inconnu")
            }
        }
    }

    private fun add(field: Int, value: Any) {
        fields.getOrPut(field) { mutableListOf() }.add(value)
    }

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun long(field: Int): Long = fields[field]?.lastOrNull() as? Long ?: 0L

    fun int(field: Int): Int = long(field).toInt()

    fun bool(field: Int): Boolean = long(field) != 0L

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun string(field: Int): String = bytes(field)?.toString(Charsets.UTF_8) ?: ""

    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::ProtoMessage)
}

class ProtocolException(message: String) : Exception(message)

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH = 2
private const val WIRE_FIXED32 = 5
private const val MAX_MESSAGE_SIZE = 1 shl 20

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var pos = start
    while (true) {
        if (pos >= bytes.size || shift >= 64) throw ProtocolException("Entier protobuf invalide")
        val b = bytes[pos++].toInt() and 0xFF
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result to pos
        shift += 7
    }
}

/** Écrit un message précédé de sa longueur (varint), comme le fait la TV. */
fun writeDelimited(out: OutputStream, message: ByteArray) {
    val buffer = ByteArrayOutputStream(message.size + 5)
    writeVarint(buffer, message.size.toLong())
    buffer.write(message)
    out.write(buffer.toByteArray())
    out.flush()
}

/** Lit un message précédé de sa longueur. Bloquant. */
fun readDelimited(input: InputStream): ByteArray {
    var length = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException("Connexion fermée par la TV")
        length = length or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift >= 35) throw ProtocolException("Longueur de message invalide")
    }
    if (length > MAX_MESSAGE_SIZE) throw ProtocolException("Message trop long")
    val data = ByteArray(length.toInt())
    var read = 0
    while (read < data.size) {
        val n = input.read(data, read, data.size - read)
        if (n < 0) throw EOFException("Connexion fermée par la TV")
        read += n
    }
    return data
}

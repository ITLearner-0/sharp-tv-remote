package io.github.itlearner0.tvremote.protocol

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class InvalidCodeException(message: String) : Exception(message)

/**
 * Appairage avec la TV (port 6467, protocole « Polo »).
 *
 * [start] fait afficher un code à 6 caractères sur la TV ; [finish] le
 * vérifie et enregistre notre certificat auprès de la TV. Appels bloquants.
 */
class PairingSession(
    private val host: String,
    private val identity: ClientIdentity,
    private val clientName: String,
    private val port: Int = PAIRING_PORT,
    private val timeoutMs: Int = 10_000,
) : Closeable {
    private var socket: SSLSocket? = null

    fun start() {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = timeoutMs
        s.startHandshake()
        send {
            message(10) { // pairing_request
                string(1, "atvremote")
                string(2, clientName)
            }
        }
        while (true) {
            val reply = receive()
            when {
                reply.has(11) -> send { // pairing_request_ack → options
                    message(20) {
                        message(1) { int(1, ENCODING_HEXADECIMAL); int(2, 6) }
                        int(3, ROLE_INPUT)
                    }
                }
                reply.has(20) -> send { // options → configuration
                    message(30) {
                        message(1) { int(1, ENCODING_HEXADECIMAL); int(2, 6) }
                        int(2, ROLE_INPUT)
                    }
                }
                reply.has(31) -> return // configuration_ack : le code est affiché
                else -> throw ProtocolException("Réponse inattendue de la TV")
            }
        }
    }

    fun finish(code: String) {
        val pin = code.trim().uppercase()
        if (!pin.matches(Regex("[0-9A-F]{6}"))) {
            throw InvalidCodeException("Le code doit contenir 6 caractères (chiffres et lettres A à F)")
        }
        val s = socket ?: throw IOException("Appairage non démarré")
        val serverKey = s.session.peerCertificates.first().publicKey as RSAPublicKey
        val clientKey = identity.certificate.publicKey as RSAPublicKey
        val secret = computeSecret(clientKey, serverKey, pin)
        if ((secret[0].toInt() and 0xFF) != pin.substring(0, 2).toInt(16)) {
            throw InvalidCodeException("Code incorrect")
        }
        send { message(40) { bytes(1, secret) } }
        val reply = receive()
        if (!reply.has(41)) throw InvalidCodeException("La TV a refusé le code")
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun send(build: ProtoWriter.() -> Unit) {
        val message = ProtoWriter().int(1, 2).int(2, STATUS_OK).apply(build).toByteArray()
        writeDelimited(socket!!.outputStream, message)
    }

    private fun receive(): ProtoMessage {
        val reply = ProtoMessage(readDelimited(socket!!.inputStream))
        val status = reply.int(2)
        if (status == STATUS_BAD_SECRET) throw InvalidCodeException("Code incorrect")
        if (status != STATUS_OK) throw ProtocolException("La TV a répondu par l'erreur $status")
        return reply
    }

    companion object {
        const val PAIRING_PORT = 6467
        private const val STATUS_OK = 200
        private const val STATUS_BAD_SECRET = 402
        private const val ROLE_INPUT = 1
        private const val ENCODING_HEXADECIMAL = 3

        /**
         * Secret d'appairage : SHA-256 des clés publiques des deux côtés et de la
         * fin du code. Le premier octet doit égaler les deux premiers caractères.
         */
        fun computeSecret(client: RSAPublicKey, server: RSAPublicKey, pin: String): ByteArray =
            MessageDigest.getInstance("SHA-256").run {
                update(hexBytes(client.modulus))
                update(hexBytes(client.publicExponent, leadingZero = true))
                update(hexBytes(server.modulus))
                update(hexBytes(server.publicExponent, leadingZero = true))
                update(hexToBytes(pin.substring(2)))
                digest()
            }

        private fun hexBytes(value: BigInteger, leadingZero: Boolean = false): ByteArray =
            hexToBytes((if (leadingZero) "0" else "") + value.toString(16))

        private fun hexToBytes(hex: String): ByteArray {
            val even = if (hex.length % 2 == 0) hex else "0$hex"
            return ByteArray(even.length / 2) { i -> even.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
        }
    }
}

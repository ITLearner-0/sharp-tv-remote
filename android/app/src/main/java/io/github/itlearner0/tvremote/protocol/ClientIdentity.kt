package io.github.itlearner0.tvremote.protocol

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Certificat client de la télécommande.
 *
 * La TV mémorise ce certificat lors de l'appairage : il doit donc rester le
 * même d'une session à l'autre. Il est créé une fois puis conservé.
 */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {

    /** Contexte TLS présentant notre certificat ; la TV utilise un certificat auto-signé, accepté tel quel. */
    fun sslContext(): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("client", privateKey, KEYSTORE_PASSWORD, arrayOf(certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, KEYSTORE_PASSWORD) }
            .keyManagers
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, arrayOf<TrustManager>(AcceptTvCertificate), SecureRandom())
        }
    }

    companion object {
        private val KEYSTORE_PASSWORD = "atvremote".toCharArray()
        private const val KEY_FILE = "client.key"
        private const val CERT_FILE = "client.crt"

        /** Charge l'identité enregistrée dans [dir], ou en crée une nouvelle. */
        fun loadOrCreate(dir: File, commonName: String = "atvremote"): ClientIdentity {
            val keyFile = File(dir, KEY_FILE)
            val certFile = File(dir, CERT_FILE)
            if (keyFile.exists() && certFile.exists()) {
                runCatching {
                    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                    val cert = CertificateFactory.getInstance("X.509")
                        .generateCertificate(certFile.inputStream().buffered()) as X509Certificate
                    return ClientIdentity(key, cert)
                }
            }
            val identity = generate(commonName)
            dir.mkdirs()
            keyFile.writeBytes(identity.privateKey.encoded)
            certFile.writeBytes(identity.certificate.encoded)
            return identity
        }

        /** Crée une clé RSA 2048 bits et un certificat X.509 auto-signé valable 10 ans. */
        fun generate(commonName: String): ClientIdentity {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val name = der(0x30, der(0x31, der(0x30, oid("2.5.4.3"), der(0x0C, commonName.toByteArray()))))
            val signatureAlgorithm = der(0x30, oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00))
            val basicConstraints = der(
                0x30,
                oid("2.5.29.19"),
                der(0x04, der(0x30, byteArrayOf(0x01, 0x01, 0xFF.toByte()), byteArrayOf(0x02, 0x01, 0x00))),
            )
            val tbs = der(
                0x30,
                der(0xA0, byteArrayOf(0x02, 0x01, 0x02)), // version 3
                der(0x02, BigInteger.valueOf(1000).toByteArray()),
                signatureAlgorithm,
                name,
                der(0x30, utcTime(Date(now - DAY_MS)), utcTime(Date(now + 3650 * DAY_MS))),
                name,
                keyPair.public.encoded,
                der(0xA3, der(0x30, basicConstraints)),
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(tbs)
                sign()
            }
            val certBytes = der(0x30, tbs, signatureAlgorithm, der(0x03, byteArrayOf(0x00) + signature))
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(certBytes.inputStream()) as X509Certificate
            return ClientIdentity(keyPair.private, cert)
        }

        private const val DAY_MS = 24L * 3600 * 1000

        private fun utcTime(date: Date): ByteArray {
            val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return der(0x17, format.format(date).toByteArray())
        }

        private fun oid(dotted: String): ByteArray {
            val parts = dotted.split('.').map { it.toLong() }
            val out = ByteArrayOutputStream()
            out.write((parts[0] * 40 + parts[1]).toInt())
            for (part in parts.drop(2)) {
                val chunks = ArrayList<Int>()
                var v = part
                do {
                    chunks.add((v and 0x7F).toInt())
                    v = v ushr 7
                } while (v > 0)
                for (i in chunks.indices.reversed()) out.write(chunks[i] or if (i > 0) 0x80 else 0)
            }
            return der(0x06, out.toByteArray())
        }

        private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
            val content = parts.fold(ByteArray(0)) { acc, part -> acc + part }
            val out = ByteArrayOutputStream()
            out.write(tag)
            val length = content.size
            when {
                length < 0x80 -> out.write(length)
                length < 0x100 -> { out.write(0x81); out.write(length) }
                length < 0x10000 -> { out.write(0x82); out.write(length shr 8); out.write(length and 0xFF) }
                else -> { out.write(0x83); out.write(length shr 16); out.write((length shr 8) and 0xFF); out.write(length and 0xFF) }
            }
            out.write(content)
            return out.toByteArray()
        }
    }
}

/** La TV présente un certificat auto-signé : on l'accepte (l'appairage par code sert d'authentification). */
@Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
private object AcceptTvCertificate : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

package io.github.itlearner0.tvremote.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Réveille la TV par le réseau (« magic packet » Wake-on-LAN). Bloquant. */
object WakeOnLan {
    private val MAC = Regex("^([0-9A-Fa-f]{2}[:-]?){5}[0-9A-Fa-f]{2}$")

    fun normalizeMac(mac: String): String? {
        val trimmed = mac.trim()
        if (!MAC.matches(trimmed)) return null
        return trimmed.replace(Regex("[:-]"), "").uppercase().chunked(2).joinToString(":")
    }

    fun packet(mac: String): ByteArray {
        val normalized = requireNotNull(normalizeMac(mac)) { "Adresse MAC invalide" }
        val macBytes = normalized.split(':').map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(6) { 0xFF.toByte() } + (1..16).fold(ByteArray(0)) { acc, _ -> acc + macBytes }
    }

    /** Envoie le paquet en diffusion générale, sur le sous-réseau de la TV et à la TV elle-même. */
    fun send(mac: String, host: String?, repeat: Int = 3) {
        val data = packet(mac)
        val targets = linkedSetOf("255.255.255.255")
        if (host != null) {
            targets += host
            val parts = host.split('.')
            if (parts.size == 4) targets += parts.take(3).joinToString(".") + ".255"
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(repeat) {
                for (target in targets) {
                    for (port in intArrayOf(9, 7)) {
                        runCatching { socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), port)) }
                    }
                }
                Thread.sleep(200)
            }
        }
    }
}

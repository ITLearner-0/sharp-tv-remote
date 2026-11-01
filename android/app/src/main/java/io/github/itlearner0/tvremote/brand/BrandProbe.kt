package io.github.itlearner0.tvremote.brand

import io.github.itlearner0.tvremote.protocol.NetworkScanner

/** TV Samsung ou LG trouvée sur le réseau local. */
data class BrandTv(val host: String, val brand: Brand, val name: String, val mac: String?)

/**
 * Recherche directe des TV Samsung (port 8001, description sur /api/v2/) et
 * LG webOS (ports 3000/3001). Appels bloquants.
 */
object BrandProbe {

    fun scan(phoneIps: List<String>): List<BrandTv> {
        val hosts = phoneIps.flatMap(NetworkScanner::candidateHosts).distinct()
        if (hosts.isEmpty()) return emptyList()
        val samsung = NetworkScanner.findOpen(hosts, port = 8001).mapNotNull { host ->
            SamsungInfo.read(host)?.let { BrandTv(host, Brand.SAMSUNG, it.name, it.mac) }
        }
        // LG : annonce SSDP « webos-second-screen » (avec le nom de la TV), et port 3001 en complément.
        val announced = ssdpLg()
        val lg = (announced.keys + NetworkScanner.findOpen(hosts, port = 3001))
            .distinct()
            .filter { host -> samsung.none { it.host == host } }
            .map { BrandTv(it, Brand.LG, announced[it] ?: "LG webOS TV", null) }
        return samsung + lg
    }

    /** TV LG qui répondent à la recherche SSDP : adresse → nom. */
    fun ssdpLg(waitMs: Int = 2_500): Map<String, String> {
        val found = linkedMapOf<String, String>()
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val search = (
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"
                    ).toByteArray()
                val group = java.net.InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(java.net.DatagramPacket(search, search.size, group, 1900)) }
                val end = System.currentTimeMillis() + waitMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    val packet = java.net.DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: java.net.SocketTimeoutException) {
                        continue
                    }
                    val host = packet.address.hostAddress ?: continue
                    if (host in found) continue
                    val reply = String(packet.data, 0, packet.length)
                    val location = Regex("(?im)^location:\\s*(\\S+)").find(reply)?.groupValues?.get(1)
                    found[host] = location?.let(::friendlyName) ?: "LG webOS TV"
                }
            }
        }
        return found
    }

    private fun friendlyName(location: String): String? = runCatching {
        LanHttp.quick.newCall(okhttp3.Request.Builder().url(location).build()).execute().use { r ->
            Regex("<friendlyName>([^<]+)</friendlyName>").find(r.body?.string().orEmpty())?.groupValues?.get(1)?.trim()
        }
    }.getOrNull()?.ifBlank { null }

    /** Marque de la TV à l'adresse [host] (saisie à la main), ou null. */
    fun detect(host: String): BrandTv? {
        SamsungInfo.read(host)?.let { return BrandTv(host, Brand.SAMSUNG, it.name, it.mac) }
        val lg = NetworkScanner.findOpen(listOf(host), port = 3000).isNotEmpty() ||
            NetworkScanner.findOpen(listOf(host), port = 3001).isNotEmpty()
        return if (lg) BrandTv(host, Brand.LG, "LG webOS TV", null) else null
    }
}

package io.github.itlearner0.tvremote.brand

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TV Samsung (Tizen, 2016 et après) : WebSocket « samsung.remote.control »,
 * sur le port 8002 (chiffré, avec jeton) ou 8001 (modèles 2016-2017).
 */
class SamsungRemote(
    private val host: String,
    private var token: String?,
    private val clientName: String,
    private val onClosed: () -> Unit,
) : BrandRemote {
    override val brand = Brand.SAMSUNG
    @Volatile private var socket: WebSocket? = null
    @Volatile override var isOpen = false
        private set
    private var playing = false
    @Volatile private var closedByUs = false

    override fun open(timeoutMs: Long): String? {
        val name = Base64.getEncoder().encodeToString(clientName.toByteArray())
        var lastError: Exception? = null
        for (secure in listOf(true, false)) {
            val url = if (secure) {
                "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name" + (token?.let { "&token=$it" } ?: "")
            } else {
                "ws://$host:8001/api/v2/channels/samsung.remote.control?name=$name"
            }
            try {
                connect(url, timeoutMs)
                return token
            } catch (e: BrandRefusedException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Samsung")
    }

    private fun connect(url: String, timeoutMs: Long) {
        val ready = CountDownLatch(1)
        var failure: Exception? = null
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("event")) {
                    "ms.channel.connect" -> {
                        message.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }?.let { token = it }
                        isOpen = true
                        ready.countDown()
                    }
                    "ms.channel.unauthorized" -> {
                        failure = BrandRefusedException("unauthorized")
                        ready.countDown()
                    }
                    "ms.channel.timeOut" -> {
                        failure = BrandRefusedException("timeout")
                        ready.countDown()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (failure == null) failure = Exception(t.message ?: t.javaClass.simpleName)
                ready.countDown()
                closed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed()
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        }
        val ws = LanHttp.client.newWebSocket(Request.Builder().url(url).build(), listener)
        socket = ws
        if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            ws.cancel()
            throw BrandRefusedException("timeout")
        }
        failure?.let {
            ws.cancel()
            throw it
        }
    }

    private fun closed() {
        val wasOpen = isOpen
        isOpen = false
        if (wasOpen && !closedByUs) onClosed()
    }

    private fun sendKey(name: String) {
        val params = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", name)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")
        socket?.send(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    override fun key(code: Int): Boolean {
        var name = BrandKeys.samsung(code) ?: return false
        if (code == io.github.itlearner0.tvremote.protocol.Keys.MEDIA_PLAY_PAUSE) {
            if (playing) name = BrandKeys.pauseOf(name) ?: name
            playing = !playing
        }
        sendKey(name)
        return true
    }

    override fun text(value: String) {
        val params = JSONObject()
            .put("Cmd", Base64.getEncoder().encodeToString(value.toByteArray()))
            .put("DataOfCmd", "base64")
            .put("TypeOfRemote", "SendInputString")
        socket?.send(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    override fun launch(appId: String) {
        // Lancement par l'API REST (la plupart des modèles), puis par le WebSocket.
        val opened = runCatching {
            LanHttp.quick.newCall(
                Request.Builder().url("http://$host:8001/api/v2/applications/$appId")
                    .post("".toRequestBody("application/json".toMediaType())).build(),
            ).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (opened) return
        val data = JSONObject().put("appId", appId).put("action_type", "DEEP_LINK")
        val params = JSONObject().put("event", "ed.apps.launch").put("to", "host").put("data", data)
        socket?.send(JSONObject().put("method", "ms.channel.emit").put("params", params).toString())
    }

    override fun powerOff() = sendKey("KEY_POWER")

    override fun macAddress(): String? = SamsungInfo.read(host)?.mac

    override fun modelName(): String? = SamsungInfo.read(host)?.name

    override fun close() {
        closedByUs = true
        isOpen = false
        socket?.close(1000, null)
        socket = null
    }
}

/** Description publiée par les TV Samsung sur http://<ip>:8001/api/v2/ (sans appairage). */
data class SamsungInfo(val name: String, val mac: String?) {
    companion object {
        fun read(host: String): SamsungInfo? = runCatching {
            LanHttp.quick.newCall(Request.Builder().url("http://$host:8001/api/v2/").build()).execute().use { r ->
                if (!r.isSuccessful) return null
                parse(r.body?.string() ?: return null)
            }
        }.getOrNull()

        fun parse(json: String): SamsungInfo? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val device = o.optJSONObject("device")
            val name = device?.optString("name")?.ifBlank { null } ?: o.optString("name").ifBlank { null } ?: return null
            val mac = device?.optString("wifiMac")?.takeIf { it.count { c -> c == ':' } == 5 }?.uppercase()
            return SamsungInfo(BrandKeys.cleanSamsungName(name), mac)
        }
    }
}

package io.github.itlearner0.tvremote.brand

import io.github.itlearner0.tvremote.protocol.Keys
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * TV LG (webOS) : protocole SSAP sur WebSocket, port 3001 (chiffré) ou 3000.
 * Les touches de navigation passent par une seconde connexion, la « prise de pointeur ».
 */
class LgRemote(
    private val host: String,
    private var clientKey: String?,
    private val onClosed: () -> Unit,
) : BrandRemote {
    override val brand = Brand.LG
    @Volatile private var socket: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    @Volatile override var isOpen = false
        private set
    @Volatile private var closedByUs = false
    private var playing = false
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    override fun open(timeoutMs: Long): String? {
        var lastError: Exception? = null
        for (url in listOf("wss://$host:3001", "ws://$host:3000")) {
            try {
                connect(url, timeoutMs)
                openPointer()
                return clientKey
            } catch (e: BrandRefusedException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                socket?.cancel()
            }
        }
        throw lastError ?: Exception("LG")
    }

    private fun connect(url: String, timeoutMs: Long) {
        val ready = CountDownLatch(1)
        var failure: Exception? = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject(MANIFEST).put("forcePairing", false).put("pairingType", "PROMPT")
                clientKey?.let { payload.put("client-key", it) }
                webSocket.send(JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                val id = message.optString("id")
                when {
                    id == "register_0" && message.optString("type") == "registered" -> {
                        message.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotBlank() }?.let { clientKey = it }
                        isOpen = true
                        ready.countDown()
                    }
                    id == "register_0" && message.optString("type") == "error" -> {
                        failure = BrandRefusedException(message.optString("error"))
                        ready.countDown()
                    }
                    else -> pending.remove(id)?.invoke(message)
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

    /** Envoie une requête SSAP et attend la réponse (au plus [timeoutMs]). */
    private fun request(uri: String, payload: JSONObject? = null, timeoutMs: Long = 0): JSONObject? {
        val ws = socket ?: return null
        val id = "r" + ids.getAndIncrement()
        val message = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        payload?.let { message.put("payload", it) }
        if (timeoutMs <= 0) {
            ws.send(message.toString())
            return null
        }
        val done = CountDownLatch(1)
        var answer: JSONObject? = null
        pending[id] = { answer = it; done.countDown() }
        ws.send(message.toString())
        done.await(timeoutMs, TimeUnit.MILLISECONDS)
        pending.remove(id)
        return answer?.optJSONObject("payload")
    }

    private fun openPointer() {
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket", timeoutMs = 4_000)
            ?.optString("socketPath")?.takeIf { it.isNotBlank() } ?: return
        pointer = LanHttp.client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (pointer === webSocket) pointer = null
            }
        })
    }

    private fun closed() {
        val wasOpen = isOpen
        isOpen = false
        pointer?.cancel()
        pointer = null
        if (wasOpen && !closedByUs) onClosed()
    }

    override fun key(code: Int): Boolean {
        when (code) {
            Keys.POWER, Keys.TV_POWER, Keys.SLEEP -> {
                powerOff()
                return true
            }
            Keys.DEL -> {
                request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1))
                return true
            }
            Keys.TV_INPUT -> {
                request("ssap://system.launcher/launch", JSONObject().put("id", "com.webos.app.inputpicker"))
                return true
            }
        }
        var name = BrandKeys.lg(code) ?: return false
        if (code == Keys.MEDIA_PLAY_PAUSE) {
            if (playing) name = BrandKeys.pauseOf(name) ?: name
            playing = !playing
        }
        val p = pointer ?: run {
            openPointer()
            pointer
        }
        if (p != null) {
            p.send("type:button\nname:$name\n\n")
        } else {
            // Sans prise de pointeur : commandes SSAP équivalentes quand elles existent.
            val uri = when (name) {
                "VOLUMEUP" -> "ssap://audio/volumeUp"
                "VOLUMEDOWN" -> "ssap://audio/volumeDown"
                "CHANNELUP" -> "ssap://tv/channelUp"
                "CHANNELDOWN" -> "ssap://tv/channelDown"
                "PLAY" -> "ssap://media.controls/play"
                "PAUSE" -> "ssap://media.controls/pause"
                "STOP" -> "ssap://media.controls/stop"
                "REWIND" -> "ssap://media.controls/rewind"
                "FASTFORWARD" -> "ssap://media.controls/fastForward"
                else -> return false
            }
            request(uri)
        }
        return true
    }

    override fun text(value: String) {
        request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", value).put("replace", 0))
    }

    override fun launch(appId: String) {
        request("ssap://system.launcher/launch", JSONObject().put("id", appId))
    }

    override fun powerOff() {
        request("ssap://system/turnOff")
    }

    override fun macAddress(): String? {
        val info = request("ssap://com.webos.service.connectionmanager/getinfo", timeoutMs = 3_000) ?: return null
        val mac = info.optJSONObject("wiredInfo")?.optString("macAddress")?.ifBlank { null }
            ?: info.optJSONObject("wifiInfo")?.optString("macAddress")?.ifBlank { null }
        return mac?.uppercase()
    }

    override fun modelName(): String? {
        val info = request("ssap://system/getSystemInfo", timeoutMs = 3_000) ?: return null
        return info.optString("modelName").ifBlank { null }?.let { "LG $it" }
    }

    override fun close() {
        closedByUs = true
        isOpen = false
        pointer?.close(1000, null)
        socket?.close(1000, null)
        pointer = null
        socket = null
    }

    private companion object {
        /** Manifeste d'appairage public des applications de télécommande webOS. */
        const val MANIFEST = """{"manifest":{"manifestVersion":1,"appVersion":"1.1","signed":{"created":"20140509","appId":"com.lge.test","vendorId":"com.lge","localizedAppNames":{"":"LG Remote App","ko-KR":"리모컨 앱","zxx-XX":"ЛГ Rэмotэ AПП"},"localizedVendorNames":{"":"LG Electronics"},"permissions":["TEST_SECURE","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD","READ_INSTALLED_APPS","READ_LGE_SDX","READ_NOTIFICATIONS","SEARCH","WRITE_SETTINGS","WRITE_NOTIFICATION_ALERT","CONTROL_POWER","READ_CURRENT_CHANNEL","READ_RUNNING_APPS","READ_UPDATE_INFO","UPDATE_FROM_REMOTE_APP","READ_LGE_TV_INPUT_EVENTS","READ_TV_CURRENT_TIME"],"serial":"2f930e2d2cfe083771f68e4fe7bb07"},"permissions":["LAUNCH","LAUNCH_WEBAPP","APP_TO_APP","CLOSE","TEST_OPEN","TEST_PROTECTED","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_TV","CONTROL_POWER","READ_APP_STATUS","READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_NETWORK_STATE","READ_RUNNING_APPS","READ_TV_CHANNEL_LIST","WRITE_NOTIFICATION_TOAST","READ_POWER_STATE","READ_COUNTRY_INFO","READ_SETTINGS","CONTROL_TV_SCREEN","CONTROL_TV_STANBY","CONTROL_FAVORITE_GROUP","CONTROL_USER_INFO","CHECK_BLUETOOTH_DEVICE","CONTROL_BLUETOOTH","CONTROL_TIMER_INFO","STB_INTERNAL_CONNECTION","CONTROL_RECORDING","READ_RECORDING_STATE","WRITE_RECORDING_LIST","READ_RECORDING_LIST","READ_RECORDING_SCHEDULE","WRITE_RECORDING_SCHEDULE","READ_STORAGE_DEVICE_LIST","READ_TV_PROGRAM_INFO","CONTROL_BOX_CHANNEL","READ_TV_ACR_AUTH_TOKEN","READ_TV_CONTENT_STATE","READ_TV_CURRENT_TIME","ADD_LAUNCHER_CHANNEL","SET_CHANNEL_SKIP","RELEASE_CHANNEL_SKIP","CONTROL_CHANNEL_BLOCK","DELETE_SELECT_CHANNEL","CONTROL_CHANNEL_GROUP","SCAN_TV_CHANNELS","CONTROL_TV_POWER","CONTROL_WOL"],"signatures":[{"signatureVersion":1,"signature":"eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw=="}]}}"""
    }
}

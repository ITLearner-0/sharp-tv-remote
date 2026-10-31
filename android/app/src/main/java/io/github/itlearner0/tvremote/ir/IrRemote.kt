package io.github.itlearner0.tvremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import io.github.itlearner0.tvremote.protocol.Keys
import org.json.JSONObject

/** Jeu de codes d'une télécommande infrarouge. */
class IrCodeset(
    val id: String,
    val brand: String,
    val name: String,
    val source: String,
    val verified: Boolean,
    val keys: Map<String, IrCode>,
)

/** Base de codes embarquée (assets/ir_codes.json, produite par tools/ir/build_ir_db.py). */
class IrDatabase(val brands: List<String>, val codesets: List<IrCodeset>) {

    fun codeset(id: String?): IrCodeset? = codesets.firstOrNull { it.id == id }

    fun forBrand(brand: String): List<IrCodeset> = codesets.filter { it.brand == brand }

    companion object {
        fun load(context: Context): IrDatabase {
            val json = JSONObject(context.assets.open("ir_codes.json").bufferedReader().use { it.readText() })
            val brands = json.getJSONArray("brands").let { a -> List(a.length()) { a.getString(it) } }
            val sets = json.getJSONArray("codesets")
            val codesets = List(sets.length()) { i ->
                val o = sets.getJSONObject(i)
                val keys = o.getJSONObject("keys")
                IrCodeset(
                    id = o.getString("id"),
                    brand = o.getString("brand"),
                    name = o.getString("name"),
                    source = o.optString("source"),
                    verified = o.optBoolean("verified"),
                    keys = keys.keys().asSequence().mapNotNull { name ->
                        parseCode(keys.getJSONObject(name))?.let { name to it }
                    }.toMap(),
                )
            }.filter { it.keys.isNotEmpty() }
            return IrDatabase(brands.filter { b -> codesets.any { it.brand == b } }, codesets)
        }

        private fun parseCode(o: JSONObject): IrCode? {
            if (o.has("raw")) {
                val raw = o.getJSONArray("raw")
                return IrCode.Raw(o.optInt("frequency", 38_000), IntArray(raw.length()) { raw.getInt(it) })
            }
            val protocol = o.getString("protocol")
            if (protocol !in IrEncoder.SUPPORTED) return null
            return IrCode.Parsed(protocol, o.getInt("address"), o.getInt("command"))
        }

        /**
         * Touche infrarouge correspondant à un code de touche Android TV,
         * avec des solutions de repli quand la télécommande d'origine n'a pas la touche.
         */
        fun irKeysFor(keyCode: Int): List<String> = when (keyCode) {
            Keys.POWER, Keys.TV_POWER -> listOf("POWER", "POWER_OFF")
            Keys.SLEEP -> listOf("POWER_OFF", "POWER")
            Keys.WAKEUP -> listOf("POWER_ON", "POWER")
            Keys.VOLUME_UP -> listOf("VOL_UP")
            Keys.VOLUME_DOWN -> listOf("VOL_DOWN")
            Keys.VOLUME_MUTE -> listOf("MUTE")
            Keys.CHANNEL_UP -> listOf("CH_UP")
            Keys.CHANNEL_DOWN -> listOf("CH_DOWN")
            Keys.DPAD_UP -> listOf("UP")
            Keys.DPAD_DOWN -> listOf("DOWN")
            Keys.DPAD_LEFT -> listOf("LEFT")
            Keys.DPAD_RIGHT -> listOf("RIGHT")
            Keys.DPAD_CENTER -> listOf("OK")
            Keys.BACK -> listOf("BACK", "EXIT")
            Keys.HOME -> listOf("HOME", "MENU")
            Keys.MENU, 256, 257, 226 -> listOf("MENU", "SETTINGS", "HOME")
            Keys.SETTINGS -> listOf("SETTINGS", "MENU")
            Keys.TV_INPUT -> listOf("INPUT")
            Keys.INFO -> listOf("INFO")
            Keys.GUIDE -> listOf("GUIDE")
            Keys.CAPTIONS -> listOf("SUBTITLE")
            Keys.LAST_CHANNEL -> listOf("PREV_CH")
            Keys.MEDIA_PLAY_PAUSE -> listOf("PLAY_PAUSE", "PLAY", "PAUSE")
            Keys.MEDIA_STOP -> listOf("STOP")
            Keys.MEDIA_REWIND -> listOf("REWIND")
            Keys.MEDIA_FAST_FORWARD -> listOf("FFWD")
            Keys.PROG_RED -> listOf("RED")
            Keys.PROG_GREEN -> listOf("GREEN")
            Keys.PROG_YELLOW -> listOf("YELLOW")
            Keys.PROG_BLUE -> listOf("BLUE")
            in Keys.digit(0)..Keys.digit(9) -> listOf("DIGIT_${keyCode - Keys.digit(0)}")
            else -> emptyList()
        }
    }
}

/** Émetteur infrarouge du téléphone. */
class IrBlaster(context: Context) {
    private val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private var toggle = false

    val available: Boolean
        get() = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    /** Émet [code]. Renvoie false si le téléphone n'a pas d'émetteur. Bloquant (durée du signal). */
    fun send(code: IrCode, newPress: Boolean = true): Boolean {
        val m = manager ?: return false
        if (!available) return false
        if (newPress) toggle = !toggle
        val signal = IrEncoder.encode(code, toggle)
        m.transmit(supportedFrequency(m, signal.frequency), signal.pattern)
        return true
    }

    /** Certains émetteurs n'acceptent qu'une plage de fréquences : on prend la plus proche. */
    private fun supportedFrequency(m: ConsumerIrManager, wanted: Int): Int {
        val ranges = runCatching { m.carrierFrequencies }.getOrNull() ?: return wanted
        if (ranges.isEmpty() || ranges.any { wanted in it.minFrequency..it.maxFrequency }) return wanted
        return ranges.minBy { minOf(kotlin.math.abs(it.minFrequency - wanted), kotlin.math.abs(it.maxFrequency - wanted)) }
            .let { wanted.coerceIn(it.minFrequency, it.maxFrequency) }
    }
}

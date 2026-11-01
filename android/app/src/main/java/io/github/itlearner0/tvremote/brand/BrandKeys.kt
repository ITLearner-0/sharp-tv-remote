package io.github.itlearner0.tvremote.brand

import io.github.itlearner0.tvremote.protocol.Keys

/**
 * Correspondance entre les touches de la télécommande (codes Android TV) et
 * les noms de touches des TV Samsung (Tizen) et LG (webOS). null : touche absente.
 * Lecture / pause : les deux TV ont des touches séparées, alternées par l'appelant.
 */
object BrandKeys {

    fun samsung(code: Int): String? = when (code) {
        Keys.HOME -> "KEY_HOME"
        Keys.BACK -> "KEY_RETURN"
        Keys.DPAD_UP -> "KEY_UP"
        Keys.DPAD_DOWN -> "KEY_DOWN"
        Keys.DPAD_LEFT -> "KEY_LEFT"
        Keys.DPAD_RIGHT -> "KEY_RIGHT"
        Keys.DPAD_CENTER -> "KEY_ENTER"
        Keys.VOLUME_UP -> "KEY_VOLUP"
        Keys.VOLUME_DOWN -> "KEY_VOLDOWN"
        Keys.VOLUME_MUTE -> "KEY_MUTE"
        Keys.POWER, Keys.TV_POWER, Keys.SLEEP -> "KEY_POWER"
        Keys.CHANNEL_UP -> "KEY_CHUP"
        Keys.CHANNEL_DOWN -> "KEY_CHDOWN"
        Keys.LAST_CHANNEL -> "KEY_PRECH"
        Keys.MENU, Keys.SETTINGS -> "KEY_MENU"
        Keys.GUIDE -> "KEY_GUIDE"
        Keys.INFO -> "KEY_INFO"
        Keys.TV_INPUT -> "KEY_SOURCE"
        Keys.CAPTIONS -> "KEY_CAPTION"
        Keys.MEDIA_PLAY_PAUSE -> "KEY_PLAY"
        Keys.MEDIA_STOP -> "KEY_STOP"
        Keys.MEDIA_REWIND, Keys.MEDIA_PREVIOUS -> "KEY_REWIND"
        Keys.MEDIA_FAST_FORWARD, Keys.MEDIA_NEXT -> "KEY_FF"
        Keys.PROG_RED -> "KEY_RED"
        Keys.PROG_GREEN -> "KEY_GREEN"
        Keys.PROG_YELLOW -> "KEY_YELLOW"
        Keys.PROG_BLUE -> "KEY_CYAN"
        in Keys.NUM_0..Keys.NUM_0 + 9 -> "KEY_${code - Keys.NUM_0}"
        else -> null
    }

    /** Touches LG envoyées par la « prise de pointeur » webOS (type:button). */
    fun lg(code: Int): String? = when (code) {
        Keys.HOME -> "HOME"
        Keys.BACK -> "BACK"
        Keys.DPAD_UP -> "UP"
        Keys.DPAD_DOWN -> "DOWN"
        Keys.DPAD_LEFT -> "LEFT"
        Keys.DPAD_RIGHT -> "RIGHT"
        Keys.DPAD_CENTER -> "ENTER"
        Keys.VOLUME_UP -> "VOLUMEUP"
        Keys.VOLUME_DOWN -> "VOLUMEDOWN"
        Keys.VOLUME_MUTE -> "MUTE"
        Keys.CHANNEL_UP -> "CHANNELUP"
        Keys.CHANNEL_DOWN -> "CHANNELDOWN"
        Keys.MENU -> "QMENU"
        Keys.SETTINGS -> "MENU"
        Keys.GUIDE -> "GUIDE"
        Keys.INFO -> "INFO"
        Keys.CAPTIONS -> "CC"
        Keys.MEDIA_PLAY_PAUSE -> "PLAY"
        Keys.MEDIA_STOP -> "STOP"
        Keys.MEDIA_REWIND, Keys.MEDIA_PREVIOUS -> "REWIND"
        Keys.MEDIA_FAST_FORWARD, Keys.MEDIA_NEXT -> "FASTFORWARD"
        Keys.PROG_RED -> "RED"
        Keys.PROG_GREEN -> "GREEN"
        Keys.PROG_YELLOW -> "YELLOW"
        Keys.PROG_BLUE -> "BLUE"
        in Keys.NUM_0..Keys.NUM_0 + 9 -> "${code - Keys.NUM_0}"
        else -> null
    }

    /** Touche « pause » correspondant à la touche lecture de [samsung] / [lg]. */
    fun pauseOf(play: String): String? = when (play) {
        "KEY_PLAY" -> "KEY_PAUSE"
        "PLAY" -> "PAUSE"
        else -> null
    }

    /** Nom affiché par Samsung (« [TV] Samsung Q60 Series (55) ») → « Samsung Q60 Series (55) ». */
    fun cleanSamsungName(name: String): String = name.removePrefix("[TV]").trim().ifBlank { "Samsung TV" }
}

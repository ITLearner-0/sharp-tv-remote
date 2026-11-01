package io.github.itlearner0.tvremote.protocol

/**
 * Bluetooth : le téléphone se présente à la TV comme un clavier doublé d'une
 * télécommande multimédia (périphérique HID standard, sans application sur la TV).
 * Traduit les touches Android de la télécommande en rapports HID.
 */
object BtHidKeys {
    const val REPORT_KEYBOARD = 1
    const val REPORT_CONSUMER = 2

    /**
     * Descripteur HID : clavier (rapport 1 : modificateurs, réservé, 6 touches)
     * et commandes multimédia (rapport 2 : un code « Consumer » sur 16 bits).
     */
    val DESCRIPTOR: ByteArray = bytes(
        // Clavier
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, REPORT_KEYBOARD,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        // Télécommande multimédia
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, REPORT_CONSUMER,
        0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
        0xC0,
    )

    /** Touche à envoyer : rapport clavier (code d'usage + modificateurs) ou multimédia. */
    sealed class Key {
        data class Keyboard(val usage: Int, val modifiers: Int = 0) : Key()
        data class Consumer(val usage: Int) : Key()
    }

    const val MOD_SHIFT = 0x02

    /** Touche Android (codes de [Keys]) → touche HID, ou null si elle n'existe pas en Bluetooth. */
    fun forKeyCode(keyCode: Int): Key? = when (keyCode) {
        Keys.DPAD_UP -> Key.Keyboard(0x52)
        Keys.DPAD_DOWN -> Key.Keyboard(0x51)
        Keys.DPAD_LEFT -> Key.Keyboard(0x50)
        Keys.DPAD_RIGHT -> Key.Keyboard(0x4F)
        Keys.DPAD_CENTER -> Key.Keyboard(0x28) // Entrée
        Keys.DEL -> Key.Keyboard(0x2A)
        Keys.BACK -> Key.Consumer(0x224) // AC Back
        Keys.HOME -> Key.Consumer(0x223) // AC Home
        Keys.MENU -> Key.Consumer(0x40)
        Keys.SEARCH -> Key.Consumer(0x221)
        Keys.SETTINGS -> Key.Consumer(0x1A0) // AL Control Panel → réglages
        Keys.GUIDE -> Key.Consumer(0x8D)
        Keys.INFO -> Key.Consumer(0x1BD)
        Keys.CAPTIONS -> Key.Consumer(0x61)
        Keys.VOLUME_UP -> Key.Consumer(0xE9)
        Keys.VOLUME_DOWN -> Key.Consumer(0xEA)
        Keys.VOLUME_MUTE -> Key.Consumer(0xE2)
        Keys.CHANNEL_UP -> Key.Consumer(0x9C)
        Keys.CHANNEL_DOWN -> Key.Consumer(0x9D)
        Keys.MEDIA_PLAY_PAUSE -> Key.Consumer(0xCD)
        Keys.MEDIA_STOP -> Key.Consumer(0xB7)
        Keys.MEDIA_REWIND -> Key.Consumer(0xB4)
        Keys.MEDIA_FAST_FORWARD -> Key.Consumer(0xB3)
        Keys.MEDIA_NEXT -> Key.Consumer(0xB5)
        Keys.MEDIA_PREVIOUS -> Key.Consumer(0xB6)
        Keys.POWER, Keys.TV_POWER -> Key.Consumer(0x30)
        Keys.SLEEP -> Key.Consumer(0x32)
        Keys.WAKEUP -> Key.Consumer(0x30)
        in Keys.NUM_0..Keys.NUM_0 + 9 -> Key.Keyboard(if (keyCode == Keys.NUM_0) 0x27 else 0x1E + keyCode - Keys.NUM_0 - 1)
        else -> null
    }

    /** Caractère → touche du clavier (disposition américaine, celle des TV par défaut). */
    fun forChar(c: Char): Key.Keyboard? = when (c) {
        in 'a'..'z' -> Key.Keyboard(0x04 + (c - 'a'))
        in 'A'..'Z' -> Key.Keyboard(0x04 + (c - 'A'), MOD_SHIFT)
        in '1'..'9' -> Key.Keyboard(0x1E + (c - '1'))
        '0' -> Key.Keyboard(0x27)
        ' ' -> Key.Keyboard(0x2C)
        '\n' -> Key.Keyboard(0x28)
        '-' -> Key.Keyboard(0x2D)
        '_' -> Key.Keyboard(0x2D, MOD_SHIFT)
        '=' -> Key.Keyboard(0x2E)
        '+' -> Key.Keyboard(0x2E, MOD_SHIFT)
        '.' -> Key.Keyboard(0x37)
        ',' -> Key.Keyboard(0x36)
        '/' -> Key.Keyboard(0x38)
        '?' -> Key.Keyboard(0x38, MOD_SHIFT)
        ';' -> Key.Keyboard(0x33)
        ':' -> Key.Keyboard(0x33, MOD_SHIFT)
        '\'' -> Key.Keyboard(0x34)
        '"' -> Key.Keyboard(0x34, MOD_SHIFT)
        '!' -> Key.Keyboard(0x1E, MOD_SHIFT)
        '@' -> Key.Keyboard(0x1F, MOD_SHIFT)
        '#' -> Key.Keyboard(0x20, MOD_SHIFT)
        '$' -> Key.Keyboard(0x21, MOD_SHIFT)
        '%' -> Key.Keyboard(0x22, MOD_SHIFT)
        '&' -> Key.Keyboard(0x24, MOD_SHIFT)
        '*' -> Key.Keyboard(0x25, MOD_SHIFT)
        '(' -> Key.Keyboard(0x26, MOD_SHIFT)
        ')' -> Key.Keyboard(0x27, MOD_SHIFT)
        else -> null
    }

    /** Rapport clavier (8 octets) : touche enfoncée, ou tout relâché si [key] est null. */
    fun keyboardReport(key: Key.Keyboard?): ByteArray =
        ByteArray(8).also {
            if (key != null) {
                it[0] = key.modifiers.toByte()
                it[2] = key.usage.toByte()
            }
        }

    /** Rapport multimédia (2 octets, petit-boutiste) : code enfoncé, ou 0 pour relâcher. */
    fun consumerReport(usage: Int): ByteArray = byteArrayOf((usage and 0xFF).toByte(), (usage shr 8 and 0xFF).toByte())

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}

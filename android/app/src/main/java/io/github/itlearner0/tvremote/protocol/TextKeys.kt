package io.github.itlearner0.tvremote.protocol

import java.text.Normalizer

/**
 * Conversion d'un texte en appuis de touches, pour les applications qui
 * ignorent le canal de saisie (IME) de la télécommande mais acceptent les
 * touches, comme un clavier branché sur la TV (disposition QWERTY standard).
 */
object TextKeys {
    data class Stroke(val keyCode: Int, val shift: Boolean = false)

    const val SHIFT_LEFT = 59
    private const val KEYCODE_A = 29
    private const val SPACE = 62
    private const val ENTER = 66

    private val SYMBOLS = mapOf(
        '.' to Stroke(56), ',' to Stroke(55), '-' to Stroke(69), '=' to Stroke(70),
        '[' to Stroke(71), ']' to Stroke(72), '\\' to Stroke(73), ';' to Stroke(74),
        '\'' to Stroke(75), '/' to Stroke(76), '@' to Stroke(77), '+' to Stroke(81),
        '*' to Stroke(17), '#' to Stroke(18), '(' to Stroke(162), ')' to Stroke(163), '`' to Stroke(68),
        // Caractères obtenus avec Maj sur un clavier QWERTY.
        '!' to Stroke(Keys.digit(1), shift = true), '$' to Stroke(Keys.digit(4), shift = true),
        '%' to Stroke(Keys.digit(5), shift = true), '&' to Stroke(Keys.digit(7), shift = true),
        '?' to Stroke(76, shift = true), ':' to Stroke(74, shift = true),
        '"' to Stroke(75, shift = true), '_' to Stroke(69, shift = true),
    )

    /** Remplace les lettres accentuées et signes typographiques par leur équivalent simple. */
    fun toAscii(text: String): String {
        val replaced = text
            .replace("œ", "oe").replace("Œ", "OE").replace("æ", "ae").replace("Æ", "AE").replace("ß", "ss")
            .replace('’', '\'').replace('‘', '\'').replace('«', '"').replace('»', '"')
            .replace('“', '"').replace('”', '"').replace('–', '-').replace('—', '-').replace(' ', ' ')
        return Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }

    /** Touches à envoyer pour [text] (déjà converti par [toAscii]) et caractères impossibles à taper. */
    fun strokes(text: String): Pair<List<Stroke>, Set<Char>> {
        val strokes = ArrayList<Stroke>()
        val unsupported = LinkedHashSet<Char>()
        for (ch in text) {
            val stroke = when (ch) {
                in 'a'..'z' -> Stroke(KEYCODE_A + (ch - 'a'))
                in 'A'..'Z' -> Stroke(KEYCODE_A + (ch - 'A'), shift = true)
                in '0'..'9' -> Stroke(Keys.digit(ch - '0'))
                ' ' -> Stroke(SPACE)
                '\n' -> Stroke(ENTER)
                else -> SYMBOLS[ch]
            }
            if (stroke != null) strokes += stroke else unsupported += ch
        }
        return strokes to unsupported
    }
}

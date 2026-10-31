package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextKeysTest {

    @Test
    fun accentsBecomePlainLetters() {
        assertEquals("Ete a Noel, coeur", TextKeys.toAscii("Été à Noël, cœur"))
        assertEquals("l'avion", TextKeys.toAscii("l’avion"))
    }

    @Test
    fun lettersDigitsAndSymbols() {
        val (strokes, unsupported) = TextKeys.strokes("Ab 9!")
        assertEquals(
            listOf(
                TextKeys.Stroke(29, shift = true), // A
                TextKeys.Stroke(30), // b
                TextKeys.Stroke(62), // espace
                TextKeys.Stroke(16), // 9
                TextKeys.Stroke(8, shift = true), // !
            ),
            strokes,
        )
        assertTrue(unsupported.isEmpty())
    }

    @Test
    fun reportsUnsupportedCharacters() {
        val (strokes, unsupported) = TextKeys.strokes("a€b")
        assertEquals(2, strokes.size)
        assertEquals(setOf('€'), unsupported)
    }
}

package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {
    private val apps = listOf("Netflix", "YouTube", "Téléviso", "Prime Video", "Disney+", "france.tv")

    @Test
    fun recognisesOpenCommands() {
        assertEquals("Netflix", VoiceCommands.appRequest("Lance Netflix"))
        assertEquals("youtube", VoiceCommands.appRequest("ouvre youtube"))
        assertNull(VoiceCommands.appRequest("films d'action"))
    }

    @Test
    fun matchesInstalledApps() {
        assertEquals("Téléviso", VoiceCommands.bestMatch("televiso", apps))
        assertEquals("Prime Video", VoiceCommands.bestMatch("prime", apps))
        assertEquals("Disney+", VoiceCommands.bestMatch("disney plus", apps))
        assertEquals("france.tv", VoiceCommands.bestMatch("France TV", apps))
        assertNull(VoiceCommands.bestMatch("canal", apps))
    }

    @Test
    fun recognisesEnglishOpenCommands() {
        org.junit.Assert.assertEquals("Netflix", VoiceCommands.appRequest("open Netflix"))
        org.junit.Assert.assertEquals("YouTube", VoiceCommands.appRequest("Launch the YouTube"))
        org.junit.Assert.assertNull(VoiceCommands.appRequest("action movies"))
    }
}

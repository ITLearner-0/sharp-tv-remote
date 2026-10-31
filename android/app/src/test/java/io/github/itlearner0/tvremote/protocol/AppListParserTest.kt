package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListParserTest {

    private val output = """
        INSTRUMENTATION_STATUS: component=com.netflix.ninja/.MainActivity
        INSTRUMENTATION_STATUS: icon=iVBORw0KGgo=
        INSTRUMENTATION_STATUS: label=Netflix
        INSTRUMENTATION_STATUS: pkg=com.netflix.ninja
        INSTRUMENTATION_STATUS: system=1
        INSTRUMENTATION_STATUS: tv=1
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_STATUS: label=Téléviso = TV
        INSTRUMENTATION_STATUS: pkg=com.example.iptv
        INSTRUMENTATION_STATUS: system=0
        INSTRUMENTATION_STATUS: tv=0
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_CODE: -1
    """.trimIndent()

    @Test
    fun readsEveryApp() {
        val apps = AppListParser.parse(output)
        assertEquals(2, apps.size)
        val netflix = apps[0]
        assertEquals("Netflix", netflix.label)
        assertEquals("com.netflix.ninja/.MainActivity", netflix.component)
        assertTrue(netflix.tvApp && netflix.system)
        assertEquals("iVBORw0KGgo=", netflix.iconBase64)
        val other = apps[1]
        assertEquals("Téléviso = TV", other.label) // le premier « = » sépare la clé de la valeur
        assertFalse(other.tvApp)
        assertEquals("", other.component)
        assertNull(other.iconBase64)
        assertTrue(AppListParser.completed(output))
    }
}

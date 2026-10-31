package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacSourcesTest {

    @Test
    fun findsMacInCommandOutputs() {
        assertEquals("A4:B1:C2:D3:E4:F5", MacSources.firstMac("a4:b1:c2:d3:e4:f5\n"))
        assertEquals(
            "A4:B1:C2:D3:E4:F5",
            MacSources.firstMac("2: eth0: <BROADCAST> mtu 1500\n    link/ether a4:b1:c2:d3:e4:f5 brd ff:ff:ff:ff:ff:ff"),
        )
        assertEquals("10:20:30:40:50:60", MacSources.firstMac("[ro.boot.wifimacaddr]: [10-20-30-40-50-60]"))
    }

    @Test
    fun ignoresPlaceholders() {
        assertNull(MacSources.firstMac("02:00:00:00:00:00"))
        assertNull(MacSources.firstMac("link/ether 00:00:00:00:00:00 brd ff:ff:ff:ff:ff:ff"))
        assertNull(MacSources.firstMac("Permission denied"))
    }

    @Test
    fun readsGoogleCastInfo() {
        val json = """{"name":"Salon","mac_address":"a4:b1:c2:d3:e4:f5","ssid":"Box"}"""
        assertEquals("A4:B1:C2:D3:E4:F5", MacSources.parseCastInfo(json))
        assertNull(MacSources.parseCastInfo("""{"name":"Salon"}"""))
    }
}

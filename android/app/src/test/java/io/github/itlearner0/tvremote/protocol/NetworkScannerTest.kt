package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class NetworkScannerTest {

    @Test
    fun candidatesCoverTheLocalSubnet() {
        val hosts = NetworkScanner.candidateHosts("192.168.1.23")
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.23" in hosts)
    }

    @Test
    fun findsOpenPorts() {
        ServerSocket(0).use { server ->
            val found = NetworkScanner.findOpen(listOf("127.0.0.1", "127.0.0.2"), port = server.localPort)
            assertTrue("127.0.0.1" in found)
        }
    }

    @Test
    fun namesFromCertificateSubjects() {
        assertEquals("SHIELD Android TV", NetworkScanner.parseNameFromSubject("CN=atvremote/darcy/darcy/SHIELD Android TV/AA:BB:CC:DD:EE:FF"))
        assertEquals("Nexus Player", NetworkScanner.parseNameFromSubject("DNQ=fugu/fugu/Nexus Player,CN=atvremote/AA:BB:CC:DD:EE:FF"))
        assertNull(NetworkScanner.parseNameFromSubject("CN=atvremote/AA:BB:CC:DD:EE:FF"))
    }
}

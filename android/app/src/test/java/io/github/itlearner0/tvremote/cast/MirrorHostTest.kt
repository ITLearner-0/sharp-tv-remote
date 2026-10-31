package io.github.itlearner0.tvremote.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class MirrorHostTest {
    @Test
    fun acceptsOnlyTheTvAddress() {
        val tv = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 42))
        assertTrue(MirrorService.sameHost(tv, "192.168.1.42"))
        assertFalse(MirrorService.sameHost(tv, "192.168.1.43"))
        assertFalse(MirrorService.sameHost(null, "192.168.1.42"))
    }
}

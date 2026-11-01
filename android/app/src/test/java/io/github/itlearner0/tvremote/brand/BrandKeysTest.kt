package io.github.itlearner0.tvremote.brand

import io.github.itlearner0.tvremote.protocol.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandKeysTest {
    @Test
    fun samsungKeys() {
        assertEquals("KEY_ENTER", BrandKeys.samsung(Keys.DPAD_CENTER))
        assertEquals("KEY_RETURN", BrandKeys.samsung(Keys.BACK))
        assertEquals("KEY_VOLUP", BrandKeys.samsung(Keys.VOLUME_UP))
        assertEquals("KEY_7", BrandKeys.samsung(Keys.digit(7)))
        assertEquals("KEY_CYAN", BrandKeys.samsung(Keys.PROG_BLUE))
        assertNull(BrandKeys.samsung(Keys.SEARCH))
    }

    @Test
    fun lgKeys() {
        assertEquals("ENTER", BrandKeys.lg(Keys.DPAD_CENTER))
        assertEquals("BACK", BrandKeys.lg(Keys.BACK))
        assertEquals("CHANNELUP", BrandKeys.lg(Keys.CHANNEL_UP))
        assertEquals("0", BrandKeys.lg(Keys.digit(0)))
        assertNull(BrandKeys.lg(Keys.SEARCH))
    }

    @Test
    fun playPauseAlternates() {
        assertEquals("KEY_PAUSE", BrandKeys.pauseOf("KEY_PLAY"))
        assertEquals("PAUSE", BrandKeys.pauseOf("PLAY"))
    }

    @Test
    fun samsungName() {
        assertEquals("Samsung Q60 Series (55)", BrandKeys.cleanSamsungName("[TV] Samsung Q60 Series (55)"))
        assertEquals("Samsung TV", BrandKeys.cleanSamsungName("[TV] "))
    }
}

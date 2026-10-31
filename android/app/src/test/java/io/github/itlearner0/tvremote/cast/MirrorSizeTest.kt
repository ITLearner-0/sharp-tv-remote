package io.github.itlearner0.tvremote.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorSizeTest {
    @Test
    fun portraitPhoneIsScaledToMultiplesOf16() {
        assertEquals(576 to 1280, MirrorService.videoSize(1080, 2340))
        assertEquals(1280 to 576, MirrorService.videoSize(2340, 1080))
    }

    @Test
    fun smallerFallbackSizes() {
        assertEquals(432 to 960, MirrorService.videoSize(1080, 2340, 960))
        assertEquals(320 to 720, MirrorService.videoSize(1080, 2340, 720))
    }

    @Test
    fun smallScreensAreNotEnlarged() {
        assertEquals(720 to 1280, MirrorService.videoSize(720, 1280))
    }
}

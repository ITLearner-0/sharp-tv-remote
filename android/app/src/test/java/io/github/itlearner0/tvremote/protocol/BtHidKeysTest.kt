package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BtHidKeysTest {
    @Test
    fun navigationKeysAreKeyboardArrows() {
        assertEquals(BtHidKeys.Key.Keyboard(0x52), BtHidKeys.forKeyCode(Keys.DPAD_UP))
        assertEquals(BtHidKeys.Key.Keyboard(0x28), BtHidKeys.forKeyCode(Keys.DPAD_CENTER))
        assertEquals(BtHidKeys.Key.Consumer(0x224), BtHidKeys.forKeyCode(Keys.BACK))
        assertEquals(BtHidKeys.Key.Consumer(0x223), BtHidKeys.forKeyCode(Keys.HOME))
    }

    @Test
    fun digitsFollowTheKeyboardRow() {
        assertEquals(BtHidKeys.Key.Keyboard(0x1E), BtHidKeys.forKeyCode(Keys.digit(1)))
        assertEquals(BtHidKeys.Key.Keyboard(0x26), BtHidKeys.forKeyCode(Keys.digit(9)))
        assertEquals(BtHidKeys.Key.Keyboard(0x27), BtHidKeys.forKeyCode(Keys.digit(0)))
    }

    @Test
    fun textUsesUsLayoutWithShift() {
        assertEquals(BtHidKeys.Key.Keyboard(0x04), BtHidKeys.forChar('a'))
        assertEquals(BtHidKeys.Key.Keyboard(0x1D, BtHidKeys.MOD_SHIFT), BtHidKeys.forChar('Z'))
        assertEquals(BtHidKeys.Key.Keyboard(0x2C), BtHidKeys.forChar(' '))
        assertNull(BtHidKeys.forChar('é'))
    }

    @Test
    fun reports() {
        assertArrayEquals(byteArrayOf(2, 0, 0x1D, 0, 0, 0, 0, 0), BtHidKeys.keyboardReport(BtHidKeys.Key.Keyboard(0x1D, 2)))
        assertArrayEquals(ByteArray(8), BtHidKeys.keyboardReport(null))
        assertArrayEquals(byteArrayOf(0x24, 0x02), BtHidKeys.consumerReport(0x224))
    }

    @Test
    fun descriptorIsBalanced() {
        val d = BtHidKeys.DESCRIPTOR
        assertEquals(2, d.count { it == 0xA1.toByte() })
        assertEquals(0xC0.toByte(), d.last())
    }
}

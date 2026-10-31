package io.github.itlearner0.tvremote.protocol

/** Codes de touches Android TV (identiques à android.view.KeyEvent). */
object Keys {
    const val SHORT = 3
    const val START_LONG = 1
    const val END_LONG = 2

    const val HOME = 3
    const val BACK = 4
    const val NUM_0 = 7
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val VOLUME_MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val GUIDE = 172
    const val CAPTIONS = 175
    const val SETTINGS = 176
    const val TV_POWER = 177
    const val TV_INPUT = 178
    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186
    const val SLEEP = 223
    const val WAKEUP = 224
    const val LAST_CHANNEL = 229

    fun digit(n: Int): Int = NUM_0 + n
}

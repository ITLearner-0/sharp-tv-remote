package io.github.itlearner0.tvremote.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itlearner0.tvremote.protocol.Keys

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val message by viewModel.messages.collectAsStateWithLifecycle()
            RemoteTheme {
                App(state, message, viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }

    /** Les boutons de volume du téléphone règlent le volume de la TV. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val tvKey = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Keys.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Keys.VOLUME_DOWN
            else -> null
        }
        if (tvKey != null && viewModel.state.value.connection == Connection.CONNECTED) {
            if (event.action == KeyEvent.ACTION_DOWN) viewModel.key(tvKey)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

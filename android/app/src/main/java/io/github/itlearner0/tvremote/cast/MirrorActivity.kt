package io.github.itlearner0.tvremote.cast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Demande l'autorisation de capturer l'écran, puis lance la duplication vers la TV. */
class MirrorActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, MirrorService::class.java)
                .putExtra(MirrorService.EXTRA_CODE, result.resultCode)
                .putExtra(MirrorService.EXTRA_DATA, result.data)
            startForegroundService(intent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            request.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }

    companion object {
        fun start(context: Context) = context.startActivity(Intent(context, MirrorActivity::class.java))
    }
}

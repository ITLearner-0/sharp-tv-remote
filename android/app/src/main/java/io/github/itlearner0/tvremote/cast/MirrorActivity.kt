package io.github.itlearner0.tvremote.cast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Demande l'autorisation de capturer l'écran, puis lance la duplication vers la TV. */
class MirrorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        // Le son des applications est capturé avec l'image : Android l'associe à l'autorisation « Micro ».
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            askCapture()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Refus : la duplication se fait quand même, sans le son.
        if (requestCode == REQUEST_AUDIO) askCapture()
    }

    private fun askCapture() {
        @Suppress("DEPRECATION")
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), REQUEST)
    }

    @Deprecated("API Activity simple, sans dépendance supplémentaire")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, MirrorService::class.java)
                    .putExtra(MirrorService.EXTRA_CODE, resultCode)
                    .putExtra(MirrorService.EXTRA_DATA, data),
            )
        }
        finish()
    }

    companion object {
        private const val REQUEST = 7
        private const val REQUEST_AUDIO = 8

        fun start(context: Context) = context.startActivity(Intent(context, MirrorActivity::class.java))
    }
}

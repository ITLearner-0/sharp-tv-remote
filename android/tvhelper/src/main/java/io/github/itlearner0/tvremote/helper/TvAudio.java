package io.github.itlearner0.tvremote.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Démarre la capture du son de la TV (« Voir la TV » sur le téléphone).
 * Activité invisible : elle obtient l'autorisation de capture (accordée d'avance
 * par le téléphone via ADB, sans fenêtre sur la TV), passe la main au service
 * AudioCapture et se ferme aussitôt. Lancée avec --es cmd stop : arrête la capture.
 */
@android.annotation.TargetApi(29)
public class TvAudio extends Activity {
    private static final int REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ("stop".equals(getIntent().getStringExtra("cmd"))) {
            stopService(new Intent(this, AudioCapture.class));
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(AudioCapture.TAG, "capture du son impossible avant Android 10");
            finish();
            return;
        }
        Log.i(AudioCapture.TAG, "demande de l'autorisation de capture");
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, AudioCapture.class)
                    .putExtra("code", resultCode)
                    .putExtra("data", data);
            startForegroundService(service);
        } else {
            Log.w(AudioCapture.TAG, "capture du son refusée (résultat " + resultCode + ")");
        }
        finish();
        overridePendingTransition(0, 0);
    }
}

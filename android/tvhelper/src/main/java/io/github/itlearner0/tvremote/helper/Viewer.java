package io.github.itlearner0.tvremote.helper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Visionneuse plein écran lancée par le téléphone (ADB) : affiche une photo ou
 * lit une vidéo servie par le téléphone sur le réseau local. Ne dépend pas de
 * Google Cast.
 *
 * am start -n …/.Viewer -a android.intent.action.VIEW -d http://… -t image/jpeg
 * Commandes : --es cmd playpause | seek (--ei delta ms) | stop
 */
public class Viewer extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView image;
    private VideoView video;
    private TextView status;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        video = new VideoView(this);
        video.setVisibility(View.GONE);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        status.setPadding(48, 32, 48, 32);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        setContentView(root);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        String cmd = intent.getStringExtra("cmd");
        if ("stop".equals(cmd)) {
            video.stopPlayback();
            finish();
            return;
        }
        if ("playpause".equals(cmd)) {
            togglePlay();
            return;
        }
        if ("seek".equals(cmd)) {
            seekBy(intent.getIntExtra("delta", 0));
            return;
        }
        Uri uri = intent.getData();
        if (uri == null) return;
        String type = intent.getType() == null ? "" : intent.getType();
        int current = ++generation;
        if (type.startsWith("video/")) showVideo(uri);
        else showImage(uri, current);
    }

    private void showVideo(Uri uri) {
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);
        video.setVisibility(View.VISIBLE);
        status.setText("Chargement de la vidéo…");
        video.setOnPreparedListener(mp -> {
            status.setText("");
            video.start();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            status.setText("Cette vidéo ne peut pas être lue par la TV (format non pris en charge).");
            return true;
        });
        video.setOnCompletionListener(mp -> status.setText("Fin de la vidéo"));
        video.setVideoURI(uri);
    }

    private void showImage(Uri uri, int current) {
        video.stopPlayback();
        video.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
        status.setText("Chargement de la photo…");
        new Thread(() -> {
            Bitmap bitmap = null;
            String error = null;
            try {
                byte[] data = download(uri.toString());
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1920) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (bitmap == null) error = "Format de photo non pris en charge par la TV.";
            } catch (Exception e) {
                error = "Photo introuvable : le téléphone et la TV doivent être sur le même réseau (" + e.getMessage() + ")";
            }
            final Bitmap result = bitmap;
            final String message = error;
            main.post(() -> {
                if (current != generation) return;
                if (result != null) image.setImageBitmap(result);
                status.setText(message == null ? "" : message);
            });
        }, "viewer-download").start();
    }

    private static byte[] download(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private void togglePlay() {
        if (video.getVisibility() != View.VISIBLE) return;
        if (video.isPlaying()) video.pause(); else video.start();
    }

    private void seekBy(int deltaMs) {
        if (video.getVisibility() != View.VISIBLE) return;
        int target = Math.max(0, video.getCurrentPosition() + deltaMs);
        if (video.getDuration() > 0) target = Math.min(target, video.getDuration() - 500);
        video.seekTo(target);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (video.getVisibility() == View.VISIBLE) video.start();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (video.getVisibility() == View.VISIBLE) video.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(30000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-10000);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        video.stopPlayback();
        super.onDestroy();
    }
}

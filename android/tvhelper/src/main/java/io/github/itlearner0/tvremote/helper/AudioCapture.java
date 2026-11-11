package io.github.itlearner0.tvremote.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Capture du son joué par les applications de la TV (Android 10 et plus) et envoi
 * au téléphone, en PCM 16 bits, sur le port {@link #PORT}.
 *
 * Mécanisme officiel d'Android (capture de lecture audio) : les applications qui
 * l'interdisent (Netflix, Prime Video, Disney+…) ne sont pas captées, et la TV
 * continue de jouer le son. Sans téléphone connecté pendant 20 s, la capture s'arrête.
 *
 * Message envoyé au téléphone : "OZAU", fréquence (int), canaux (int), puis le son.
 */
@android.annotation.TargetApi(29)
public class AudioCapture extends Service {
    static final String TAG = "OmniZapAudio";
    static final int PORT = 47321;
    private static final int RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int MAGIC = 0x4F5A4155; // "OZAU"
    private static final int IDLE_MS = 20_000;

    private volatile boolean running;
    private volatile ServerSocket server;
    private MediaProjection projection;
    private AudioRecord record;
    private Thread worker;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground();
        if (running || intent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return START_NOT_STICKY;
        Intent data = intent.getParcelableExtra("data");
        int code = intent.getIntExtra("code", 0);
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            projection = manager.getMediaProjection(code, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "capture arrêtée par le système");
                    stopSelf();
                }
            }, new Handler(Looper.getMainLooper()));
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(min * 4, RATE * CHANNELS * 2 / 5))
                    .setAudioPlaybackCaptureConfig(config)
                    .build();
            record.startRecording();
        } catch (Exception e) {
            Log.w(TAG, "capture du son impossible : " + e);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        worker = new Thread(this::serve, "tv-audio");
        worker.start();
        Log.i(TAG, "capture du son prête sur le port " + PORT);
        return START_NOT_STICKY;
    }

    /** Un téléphone à la fois ; un nouveau remplace le précédent. */
    private void serve() {
        try (ServerSocket s = new ServerSocket(PORT)) {
            server = s;
            s.setReuseAddress(true);
            s.setSoTimeout(IDLE_MS);
            byte[] buffer = new byte[RATE * CHANNELS * 2 / 50]; // 20 ms
            while (running) {
                Socket client;
                try {
                    client = s.accept();
                } catch (SocketTimeoutException e) {
                    Log.i(TAG, "aucun téléphone : arrêt de la capture");
                    break;
                }
                Log.i(TAG, "téléphone connecté : " + client.getInetAddress());
                try (Socket c = client) {
                    c.setTcpNoDelay(true);
                    OutputStream raw = c.getOutputStream();
                    DataOutputStream out = new DataOutputStream(raw);
                    out.writeInt(MAGIC);
                    out.writeInt(RATE);
                    out.writeInt(CHANNELS);
                    out.flush();
                    long nextReport = System.currentTimeMillis() + 5000;
                    int peak = 0;
                    long total = 0;
                    while (running) {
                        int n = record.read(buffer, 0, buffer.length);
                        if (n < 0) throw new IllegalStateException("lecture du son : " + n);
                        if (n > 0) raw.write(buffer, 0, n);
                        total += n;
                        for (int i = 0; i + 1 < n; i += 8) {
                            int sample = Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8)));
                            if (sample > peak) peak = sample;
                        }
                        // Diagnostic : niveau du son capté toutes les 5 s (0 = silence).
                        if (System.currentTimeMillis() > nextReport) {
                            Log.i(TAG, "son capté : " + total + " octets, niveau max " + peak);
                            nextReport = System.currentTimeMillis() + 5000;
                            peak = 0;
                        }
                    }
                } catch (Exception e) {
                    Log.i(TAG, "téléphone déconnecté : " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "serveur du son : " + e);
        } finally {
            server = null;
            new Handler(Looper.getMainLooper()).post(this::stopSelf);
        }
    }

    private void foreground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("audio", "OmniZap", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, "audio")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("OmniZap")
                .setContentText("Son de la TV envoyé au téléphone")
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        ServerSocket s = server;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        if (worker != null) worker.interrupt();
        if (record != null) {
            try {
                record.stop();
            } catch (Exception ignored) {
            }
            record.release();
            record = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        super.onDestroy();
    }
}

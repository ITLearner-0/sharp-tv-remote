package io.github.itlearner0.tvremote.helper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Duplication de l'écran du téléphone : se connecte au téléphone (adresse et
 * port reçus par ADB), reçoit l'écran encodé en H.264 et l'affiche en plein
 * écran. Ne dépend ni de Google Cast ni de Miracast.
 *
 * Flux : « MIRR », puis des messages : 0 = format (largeur, hauteur),
 * 1 = image (drapeaux, horodatage µs, taille, données).
 *
 * am start -n …/.Mirror --es host 192.168.1.20 --ei port 40000
 * am start -n …/.Mirror --es cmd stop
 */
public class Mirror extends Activity implements SurfaceHolder.Callback {
    private static final int MAGIC = 0x4D495252; // "MIRR"
    /** Étiquette du journal système, relu par le téléphone (ADB) en cas d'échec. */
    private static final String TAG = "OmniZapMirror";
    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private SurfaceView view;
    private TextView status;
    private Surface surface;
    private String host;
    private int port;
    private volatile boolean running;
    private volatile Socket socket;
    private Thread worker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        status.setPadding(48, 32, 48, 32);
        status.setText("Connexion au téléphone…");
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        setContentView(root);
        readTarget(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ("stop".equals(intent.getStringExtra("cmd"))) {
            finish();
            return;
        }
        readTarget(intent);
        restart();
    }

    private void readTarget(Intent intent) {
        if (intent.getStringExtra("host") != null) {
            host = intent.getStringExtra("host");
            port = intent.getIntExtra("port", 0);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        restart();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopWorker();
        surface = null;
    }

    private void restart() {
        stopWorker();
        if (surface == null || host == null || port <= 0) return;
        running = true;
        final Surface target = surface;
        worker = new Thread(() -> receive(target), "mirror");
        worker.start();
    }

    private void stopWorker() {
        running = false;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        Thread t = worker;
        if (t != null) {
            try {
                t.join(1500);
            } catch (InterruptedException ignored) {
            }
        }
        worker = null;
    }

    private void receive(Surface target) {
        MediaCodec codec = null;
        String end = "Duplication terminée.";
        int frames = 0;
        try (Socket s = new Socket()) {
            socket = s;
            Log.i(TAG, "connexion au téléphone " + host + ":" + port);
            s.connect(new InetSocketAddress(host, port), 6000);
            Log.i(TAG, "connecté");
            s.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
            if (in.readInt() != MAGIC) throw new IllegalStateException("flux inconnu");
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (running) {
                int type = in.readInt();
                if (type == 0) {
                    int w = in.readInt();
                    int h = in.readInt();
                    if (codec != null) {
                        codec.stop();
                        codec.release();
                    }
                    Log.i(TAG, "format " + w + "x" + h);
                    try {
                        codec = decoder(w, h, target);
                    } catch (Exception e) {
                        throw new IllegalStateException("la TV ne sait pas décoder une image de " + w + " × " + h + " (" + e + ")", e);
                    }
                    Log.i(TAG, "décodeur prêt : " + codec.getName());
                    main.post(() -> fit(w, h));
                    continue;
                }
                int flags = in.readInt();
                long pts = in.readLong();
                int length = in.readInt();
                if (length < 0 || length > 16 * 1024 * 1024) throw new IllegalStateException("image invalide");
                byte[] data = new byte[length];
                in.readFully(data);
                if (codec == null) continue;
                int index;
                while ((index = codec.dequeueInputBuffer(10_000)) < 0) {
                    drain(codec, info);
                    if (!running) return;
                }
                ByteBuffer buffer = codec.getInputBuffer(index);
                buffer.clear();
                if (buffer.remaining() < length) {
                    throw new IllegalStateException("image de " + length + " octets, tampon du décodeur de " + buffer.remaining());
                }
                buffer.put(data);
                boolean config = (flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                codec.queueInputBuffer(index, 0, length, pts, config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0);
                frames++;
                drain(codec, info);
            }
        } catch (java.io.EOFException e) {
            end = "Duplication arrêtée par le téléphone.";
            Log.i(TAG, end + " Images reçues : " + frames);
        } catch (Exception e) {
            end = "Duplication interrompue : " + e;
            Log.e(TAG, "ERREUR après " + frames + " images : " + e, e);
        } finally {
            socket = null;
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
        }
        if (running) {
            final String message = end + (frames > 0 ? "" : " (aucune image reçue)");
            main.post(() -> {
                status.setText(message);
                status.setVisibility(View.VISIBLE);
                main.postDelayed(this::finish, 20000);
            });
        }
    }

    /** Décodeur H.264 ; sans le mode « faible latence » si la TV le refuse. */
    private static MediaCodec decoder(int w, int h, Surface target) throws Exception {
        Exception first = null;
        for (boolean lowLatency : new boolean[] {true, false}) {
            if (lowLatency && Build.VERSION.SDK_INT < 30) continue;
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
            // Tampon assez grand pour une image complète (1920 × 1080).
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024);
            if (lowLatency) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            try {
                codec.configure(format, target, null, 0);
                codec.start();
                return codec;
            } catch (Exception e) {
                Log.w(TAG, "décodeur refusé (faible latence : " + lowLatency + ") : " + e);
                codec.release();
                if (first == null) first = e;
            }
        }
        throw first;
    }

    private static void drain(MediaCodec codec, MediaCodec.BufferInfo info) {
        while (true) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index >= 0) codec.releaseOutputBuffer(index, true);
            else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return;
        }
    }

    /** Surface à la taille de l'image du téléphone, centrée (bandes noires sur les côtés). */
    private void fit(int w, int h) {
        status.setVisibility(View.GONE);
        int width = root.getWidth();
        int height = root.getHeight();
        if (width == 0 || height == 0 || w == 0 || h == 0) return;
        float scale = Math.min(width / (float) w, height / (float) h);
        view.setLayoutParams(new FrameLayout.LayoutParams(Math.round(w * scale), Math.round(h * scale), Gravity.CENTER));
    }

    @Override
    protected void onDestroy() {
        stopWorker();
        super.onDestroy();
    }
}

package io.github.itlearner0.tvremote.helper;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Lancée par « am instrument -r -w », elle renvoie à la télécommande chaque
 * application que l'écran d'accueil peut lancer : nom, activité, type (TV ou
 * téléphone) et icône. Rien n'est conservé sur la TV.
 */
public class AppLister extends Instrumentation {
    private static final int ICON_SIZE = 96;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            if (!app.enabled || app.packageName.equals(getContext().getPackageName())) continue;
            Intent tvIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
            Intent intent = tvIntent != null ? tvIntent : pm.getLaunchIntentForPackage(app.packageName);
            if (intent == null) continue; // composant interne, sans icône sur l'écran d'accueil
            Bundle item = new Bundle();
            item.putString("pkg", app.packageName);
            item.putString("label", clean(String.valueOf(pm.getApplicationLabel(app))));
            item.putString("tv", tvIntent != null ? "1" : "0");
            item.putString("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? "1" : "0");
            if (intent.getComponent() != null) item.putString("component", intent.getComponent().flattenToShortString());
            String icon = icon(pm, app);
            if (icon != null) item.putString("icon", icon);
            sendStatus(1, item);
        }
        finish(Activity.RESULT_OK, new Bundle());
    }

    private static String clean(String label) {
        return label.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** Icône (bannière TV de préférence) en PNG 96 px, encodée en base64 sur une ligne. */
    private static String icon(PackageManager pm, ApplicationInfo app) {
        try {
            Drawable drawable = pm.getApplicationIcon(app);
            Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
            drawable.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable e) {
            return null;
        }
    }
}

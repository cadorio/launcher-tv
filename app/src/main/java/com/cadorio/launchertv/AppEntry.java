package com.cadorio.launchertv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

/** Um app que pode ser aberto a partir do launcher. */
final class AppEntry {
    final String key;
    final String label;
    final ComponentName component;
    /** true quando o app declara LEANBACK_LAUNCHER, ou seja, foi feito para TV. */
    final boolean leanback;
    private final ActivityInfo info;

    private Drawable banner;
    private Drawable icon;
    private boolean bannerLoaded;
    private int tileColor;
    private boolean tileColorLoaded;

    AppEntry(ActivityInfo info, String label, boolean leanback) {
        this.info = info;
        this.label = label;
        this.leanback = leanback;
        this.component = new ComponentName(info.packageName, info.name);
        this.key = component.flattenToShortString();
    }

    String packageName() {
        return component.getPackageName();
    }

    /** O banner 16:9 que apps de TV fornecem; null se o app não tiver um. */
    Drawable banner(PackageManager pm) {
        if (!bannerLoaded) {
            bannerLoaded = true;
            try {
                banner = info.loadBanner(pm);
                if (banner == null) banner = info.applicationInfo.loadBanner(pm);
            } catch (RuntimeException ignored) {
                banner = null;
            }
        }
        return banner;
    }

    /**
     * O ícone do app na maior densidade que ele oferece. O loadIcon() comum devolve a versão da
     * densidade da TV (muitas vezes 96 px), que fica borrada esticada num bloco grande.
     */
    Drawable icon(PackageManager pm) {
        if (icon == null) {
            try {
                int res = info.getIconResource(); // o da activity, ou o do app
                if (res != 0) {
                    Resources r = pm.getResourcesForApplication(info.applicationInfo);
                    icon = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                icon = null;
            }
            if (icon == null) {
                try {
                    icon = info.loadIcon(pm);
                } catch (RuntimeException e) {
                    icon = pm.getDefaultActivityIcon();
                }
            }
        }
        return icon;
    }

    /**
     * A cor predominante do ícone, escurecida para o nome em branco continuar legível.
     * Usada como fundo do bloco quando o app não tem banner, para ele parecer o "cartão" do app.
     * Devolve 0 se não der para calcular (ícone transparente ou que falhou ao desenhar).
     */
    int tileColor(PackageManager pm) {
        if (!tileColorLoaded) {
            tileColorLoaded = true;
            tileColor = averageColor(icon(pm));
        }
        return tileColor;
    }

    static int averageColor(Drawable d) {
        if (d == null) return 0;
        final int size = 24;
        try {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = bmp.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    n++;
                }
            }
            bmp.recycle();
            if (n < size * size / 8) return 0;
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
            hsv[2] = Math.min(hsv[2], 0.55f); // escurece: texto branco por cima
            return Color.HSVToColor(hsv);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    Intent launchIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }
}

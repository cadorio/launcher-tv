package com.cadorio.launchertv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

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

    Drawable icon(PackageManager pm) {
        if (icon == null) {
            try {
                icon = info.loadIcon(pm);
            } catch (RuntimeException e) {
                icon = pm.getDefaultActivityIcon();
            }
        }
        return icon;
    }

    Intent launchIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }
}

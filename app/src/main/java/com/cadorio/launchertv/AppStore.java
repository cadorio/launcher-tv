package com.cadorio.launchertv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lista os apps instalados e guarda a organização do usuário: quais apps ficam nos favoritos
 * (e em que ordem) e quais ficam ocultos. Tudo fica em SharedPreferences, sem internet.
 */
final class AppStore {
    private static final String PREFS = "launcher";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_SECONDS = "show_seconds";
    private static final String KEY_WALLPAPER = "use_wallpaper";
    private static final String KEY_INITIALISED = "initialised";

    /**
     * Apps de streaming comuns. Na primeira execução, os que estiverem instalados viram os
     * favoritos iniciais, nesta ordem. O usuário reorganiza depois.
     */
    private static final List<String> DEFAULT_FAVORITES = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.globo.globotv",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
            "com.plexapp.android",
            "org.jellyfin.androidtv",
            "org.xbmc.kodi");
    private static final int MAX_DEFAULT_FAVORITES = 6;

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, AppEntry> apps = new LinkedHashMap<>();

    AppStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Relê a lista de apps do sistema. Apps de TV (leanback) têm preferência sobre os de celular. */
    void reload() {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();

        for (boolean leanback : new boolean[] {true, false}) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || byPackage.containsKey(pkg)) continue;
                CharSequence label = ri.loadLabel(pm);
                byPackage.put(pkg, new AppEntry(ri.activityInfo,
                        label == null ? pkg : label.toString().trim(), leanback));
            }
        }

        List<AppEntry> sorted = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(sorted, (a, b) -> collator.compare(a.label, b.label));

        apps.clear();
        for (AppEntry e : sorted) apps.put(e.key, e);

        if (!prefs.getBoolean(KEY_INITIALISED, false)) seedFavorites();
    }

    private void seedFavorites() {
        List<String> favorites = new ArrayList<>();
        for (String pkg : DEFAULT_FAVORITES) {
            if (favorites.size() >= MAX_DEFAULT_FAVORITES) break;
            for (AppEntry e : apps.values()) {
                if (e.packageName().equals(pkg)) {
                    favorites.add(e.key);
                    break;
                }
            }
        }
        prefs.edit()
                .putString(KEY_FAVORITES, String.join("\n", favorites))
                .putBoolean(KEY_INITIALISED, true)
                .apply();
    }

    /** Favoritos instalados, na ordem escolhida pelo usuário. */
    List<AppEntry> favorites() {
        List<AppEntry> out = new ArrayList<>();
        for (String key : favoriteKeys()) {
            AppEntry e = apps.get(key);
            if (e != null && !isHidden(e)) out.add(e);
        }
        return out;
    }

    /** Todos os apps visíveis, em ordem alfabética. */
    List<AppEntry> allVisible() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps.values()) if (!isHidden(e)) out.add(e);
        return out;
    }

    boolean isFavorite(AppEntry e) {
        return favoriteKeys().contains(e.key);
    }

    void addFavorite(AppEntry e) {
        List<String> keys = favoriteKeys();
        if (!keys.contains(e.key)) keys.add(e.key);
        saveFavorites(keys);
    }

    void removeFavorite(AppEntry e) {
        List<String> keys = favoriteKeys();
        keys.remove(e.key);
        saveFavorites(keys);
    }

    /**
     * Move um favorito uma posição para a esquerda (delta -1) ou direita (+1), considerando apenas
     * os favoritos visíveis. Retorna a nova posição visível, ou -1 se não houve mudança.
     */
    int moveFavorite(AppEntry e, int delta) {
        List<AppEntry> visible = favorites();
        int from = indexOf(visible, e);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= visible.size()) return -1;

        List<String> keys = favoriteKeys();
        int a = keys.indexOf(visible.get(from).key);
        int b = keys.indexOf(visible.get(to).key);
        Collections.swap(keys, a, b);
        saveFavorites(keys);
        return to;
    }

    void hide(AppEntry e) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, Collections.emptySet()));
        hidden.add(e.key);
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
    }

    int hiddenCount() {
        int n = 0;
        for (AppEntry e : apps.values()) if (isHidden(e)) n++;
        return n;
    }

    void unhideAll() {
        prefs.edit().remove(KEY_HIDDEN).apply();
    }

    void resetLayout() {
        prefs.edit().remove(KEY_FAVORITES).remove(KEY_HIDDEN).remove(KEY_INITIALISED).apply();
        seedFavorites();
    }

    boolean showSeconds() {
        return prefs.getBoolean(KEY_SECONDS, false);
    }

    void toggleSeconds() {
        prefs.edit().putBoolean(KEY_SECONDS, !showSeconds()).apply();
    }

    boolean useWallpaper() {
        return prefs.getBoolean(KEY_WALLPAPER, false);
    }

    void toggleWallpaper() {
        prefs.edit().putBoolean(KEY_WALLPAPER, !useWallpaper()).apply();
    }

    private boolean isHidden(AppEntry e) {
        return prefs.getStringSet(KEY_HIDDEN, Collections.emptySet()).contains(e.key);
    }

    private List<String> favoriteKeys() {
        String raw = prefs.getString(KEY_FAVORITES, "");
        List<String> keys = new ArrayList<>();
        for (String k : raw.split("\n")) if (!k.isEmpty()) keys.add(k);
        return keys;
    }

    private void saveFavorites(List<String> keys) {
        prefs.edit().putString(KEY_FAVORITES, String.join("\n", keys)).apply();
    }

    private static int indexOf(List<AppEntry> list, AppEntry e) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(e.key)) return i;
        return -1;
    }
}

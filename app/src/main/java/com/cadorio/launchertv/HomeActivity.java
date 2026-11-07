package com.cadorio.launchertv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A tela inicial: relógio, uma fileira de favoritos e a grade com todos os apps.
 * Feita para controle remoto (D-pad): o item em foco cresce e mostra o nome.
 */
public class HomeActivity extends Activity {
    private static final int GRID_COLUMNS = 5;
    private static final String TAG_FAVORITE = "fav:";
    private static final String TAG_GRID = "all:";

    private AppStore store;
    private PackageManager pm;

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout favoritesRow;
    private LinearLayout grid;
    private TextView emptyFavorites;
    private TextClock clock;
    private TextClock date;

    private boolean dirty = true;
    private int sidePadding;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dirty = true;
            if (hasWindowFocus()) refresh(null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        pm = getPackageManager();
        sidePadding = dp(56);
        buildLayout();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (dirty) refresh(null);
    }

    /** Apertar Home estando já na tela inicial volta ao topo. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && hasWindowFocus()) goHome();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // A tela inicial não tem para onde voltar: Voltar leva ao topo, como no Apple TV.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) goHome();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ---------------------------------------------------------------- layout

    private void buildLayout() {
        root = new FrameLayout(this);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, dp(40), 0, dp(56));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());

        content.addView(sectionTitle(getString(R.string.favorites)));
        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipToPadding(false);
        favScroll.setClipChildren(false);
        favScroll.setPadding(sidePadding, dp(18), sidePadding, dp(34));
        favoritesRow = new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRow.setClipChildren(false);
        favScroll.addView(favoritesRow);
        content.addView(favScroll);

        emptyFavorites = new TextView(this);
        emptyFavorites.setText(R.string.empty_favorites);
        emptyFavorites.setTextColor(getColor(R.color.text_secondary));
        emptyFavorites.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyFavorites.setPadding(sidePadding, 0, sidePadding, dp(24));
        content.addView(emptyFavorites);

        content.addView(sectionTitle(getString(R.string.all_apps)));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(sidePadding, dp(18), sidePadding, 0);
        content.addView(grid);

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(sidePadding, 0, sidePadding, dp(16));

        LinearLayout clocks = new LinearLayout(this);
        clocks.setOrientation(LinearLayout.VERTICAL);

        clock = new TextClock(this);
        clock.setTextColor(getColor(R.color.text_primary));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clocks.addView(clock);

        date = new TextClock(this);
        date.setTextColor(getColor(R.color.text_secondary));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        Locale locale = Locale.getDefault();
        String datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        clocks.addView(date);

        header.addView(clocks, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView settings = new ImageView(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setBackgroundResource(R.drawable.tile_bg);
        settings.setPadding(dp(12), dp(12), dp(12), dp(12));
        settings.setFocusable(true);
        settings.setContentDescription(getString(R.string.settings));
        settings.setOnClickListener(v -> showSettings());
        settings.setOnFocusChangeListener((v, focused) -> animateFocus(v, v, focused));
        header.addView(settings, new LinearLayout.LayoutParams(dp(56), dp(56)));

        return header;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(getColor(R.color.text_secondary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(sidePadding, dp(8), sidePadding, 0);
        return title;
    }

    // ---------------------------------------------------------------- data → views

    /** Relê os apps e redesenha as fileiras, devolvendo o foco ao mesmo app quando possível. */
    private void refresh(String focusTag) {
        if (focusTag == null) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof String) focusTag = (String) focused.getTag();
        }
        dirty = false;
        store.reload();
        applyBackground();
        applyClockFormat();

        int usable = getResources().getDisplayMetrics().widthPixels - 2 * sidePadding;
        int gap = dp(22);

        // Favoritos: um pouco maiores que a grade, como no Apple TV.
        int favWidth = (usable - 4 * gap) * 10 / 42;
        favoritesRow.removeAllViews();
        List<AppEntry> favorites = store.favorites();
        for (int i = 0; i < favorites.size(); i++) {
            View cell = buildTile(favorites.get(i), TAG_FAVORITE, favWidth);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = gap;
            favoritesRow.addView(cell, lp);
        }
        emptyFavorites.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);

        int gridWidth = (usable - (GRID_COLUMNS - 1) * gap) / GRID_COLUMNS;
        grid.removeAllViews();
        List<AppEntry> all = store.allVisible();
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % GRID_COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(6);
                grid.addView(row, rowLp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % GRID_COLUMNS > 0) lp.leftMargin = gap;
            row.addView(buildTile(all.get(i), TAG_GRID, gridWidth), lp);
        }

        restoreFocus(focusTag);
    }

    /** Um bloco 16:9 com o banner do app (ou ícone + nome) e o nome embaixo, visível no foco. */
    private View buildTile(AppEntry app, String section, int width) {
        int height = width * 9 / 16;

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.tile_bg);
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setTag(section + app.key);
        tile.setContentDescription(app.label);

        Drawable banner = app.banner(pm);
        if (banner != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon(pm));
            int iconSize = height * 45 / 100;
            inner.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            TextView name = new TextView(this);
            name.setText(app.label);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(height / 9f, sp(11)));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(8), dp(6), dp(8), 0);
            inner.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tile.addView(inner, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        cell.addView(tile, new LinearLayout.LayoutParams(width, height));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, dp(14), 0, dp(8));
        label.setAlpha(0f);
        cell.addView(label, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        tile.setOnFocusChangeListener((v, focused) -> {
            animateFocus(cell, v, focused);
            label.animate().alpha(focused ? 1f : 0f).setDuration(150).start();
        });
        tile.setOnClickListener(v -> launch(app, v));
        tile.setOnLongClickListener(v -> {
            showAppOptions(app, section);
            return true;
        });
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                showAppOptions(app, section);
                return true;
            }
            return false;
        });
        return cell;
    }

    /** O item em foco cresce e fica por cima dos vizinhos. */
    private void animateFocus(View container, View target, boolean focused) {
        float scale = focused ? 1.1f : 1f;
        container.setTranslationZ(focused ? dp(12) : 0);
        target.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
    }

    private void restoreFocus(String tag) {
        View target = tag == null ? null : root.findViewWithTag(tag);
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    private View firstTile() {
        if (favoritesRow.getChildCount() > 0) return tileOf(favoritesRow.getChildAt(0));
        if (grid.getChildCount() > 0) return tileOf(((ViewGroup) grid.getChildAt(0)).getChildAt(0));
        return null;
    }

    private static View tileOf(View cell) {
        return ((ViewGroup) cell).getChildAt(0);
    }

    private void goHome() {
        scroll.smoothScrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

    // ---------------------------------------------------------------- actions

    private void launch(AppEntry app, View from) {
        try {
            Bundle options = ActivityOptions.makeScaleUpAnimation(
                    from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            startActivity(app.launchIntent(), options);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show();
            dirty = true;
        }
    }

    private void showAppOptions(AppEntry app, String section) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (store.isFavorite(app)) {
            if (TAG_FAVORITE.equals(section)) {
                labels.add(getString(R.string.move_left));
                actions.add(() -> refresh(store.moveFavorite(app, -1) >= 0 ? TAG_FAVORITE + app.key : null));
                labels.add(getString(R.string.move_right));
                actions.add(() -> refresh(store.moveFavorite(app, 1) >= 0 ? TAG_FAVORITE + app.key : null));
            }
            labels.add(getString(R.string.remove_favorite));
            actions.add(() -> {
                store.removeFavorite(app);
                refresh(TAG_GRID + app.key);
            });
        } else {
            labels.add(getString(R.string.add_favorite));
            actions.add(() -> {
                store.addFavorite(app);
                refresh(TAG_FAVORITE + app.key);
            });
        }
        labels.add(getString(R.string.hide_app));
        actions.add(() -> {
            store.hide(app);
            Toast.makeText(this, getString(R.string.hidden_done, app.label), Toast.LENGTH_LONG).show();
            refresh(null);
        });
        labels.add(getString(R.string.app_info));
        actions.add(() -> openSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName(), null))));

        showMenu(app.label, labels, actions);
    }

    private void showSettings() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add(getString(R.string.system_settings));
        actions.add(() -> openSafely(new Intent(Settings.ACTION_SETTINGS)));

        int hidden = store.hiddenCount();
        if (hidden > 0) {
            labels.add(getString(R.string.show_hidden, hidden));
            actions.add(() -> {
                store.unhideAll();
                refresh(null);
            });
        }
        labels.add(getString(R.string.toggle_seconds));
        actions.add(() -> {
            store.toggleSeconds();
            applyClockFormat();
        });
        labels.add(getString(R.string.toggle_wallpaper));
        actions.add(() -> {
            store.toggleWallpaper();
            applyBackground();
        });
        labels.add(getString(R.string.reset_layout));
        actions.add(() -> {
            store.resetLayout();
            refresh(null);
        });

        showMenu(getString(R.string.settings), labels, actions);
    }

    private void showMenu(String title, List<String> labels, List<Runnable> actions) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void openSafely(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.launch_failed, ""), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- appearance

    private void applyClockFormat() {
        Locale locale = Locale.getDefault();
        boolean seconds = store.showSeconds();
        clock.setFormat24Hour(DateFormat.getBestDateTimePattern(locale, seconds ? "Hms" : "Hm"));
        clock.setFormat12Hour(DateFormat.getBestDateTimePattern(locale, seconds ? "hms" : "hm"));
    }

    /** Gradiente próprio, ou o papel de parede da TV com um véu escuro por cima. */
    private void applyBackground() {
        if (store.useWallpaper()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(null);
            root.setBackgroundResource(R.drawable.scrim);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawableResource(R.drawable.background);
            root.setBackground(null);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}

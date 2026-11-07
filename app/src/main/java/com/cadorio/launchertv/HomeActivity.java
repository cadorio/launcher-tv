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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
    private static final String TAG_FAVORITE = "fav:";
    private static final String TAG_GRID = "all:";
    private static final String TAG_INPUT = "in:";

    private AppStore store;
    private PackageManager pm;

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout favoritesRow;
    private LinearLayout inputsRow;
    private View inputsTitle;
    private View inputsScroll;
    private LinearLayout grid;
    private TextView emptyFavorites;
    private TextClock clock;
    private TextClock date;
    private ImageView settingsButton;
    /** Textos em cor secundária (títulos, data…), recoloridos quando o tema muda. */
    private final List<TextView> secondaryTexts = new ArrayList<>();
    private Theme theme;

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
        // Botão verde do controle (Sony, Philips, TCL…): ajustes do launcher de qualquer lugar.
        if (event.getKeyCode() == KeyEvent.KEYCODE_PROG_GREEN) {
            if (event.getAction() == KeyEvent.ACTION_UP) showSettings();
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
        secondaryTexts.add(emptyFavorites);
        emptyFavorites.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyFavorites.setPadding(sidePadding, 0, sidePadding, dp(24));
        content.addView(emptyFavorites);

        TextView inTitle = sectionTitle(getString(R.string.inputs));
        inputsTitle = inTitle;
        content.addView(inTitle);
        HorizontalScrollView inScroll = new HorizontalScrollView(this);
        inScroll.setHorizontalScrollBarEnabled(false);
        inScroll.setClipToPadding(false);
        inScroll.setClipChildren(false);
        inScroll.setPadding(sidePadding, dp(18), sidePadding, dp(34));
        inputsRow = new LinearLayout(this);
        inputsRow.setOrientation(LinearLayout.HORIZONTAL);
        inputsRow.setClipChildren(false);
        inScroll.addView(inputsRow);
        inputsScroll = inScroll;
        content.addView(inScroll);

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
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clocks.addView(clock);

        date = new TextClock(this);
        secondaryTexts.add(date);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        Locale locale = Locale.getDefault();
        String datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        clocks.addView(date);

        header.addView(clocks, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView settings = new ImageView(this);
        settings.setImageResource(R.drawable.ic_settings);
        settingsButton = settings;
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
        secondaryTexts.add(title);
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
        theme = store.theme();
        applyTheme();
        applyClockFormat();

        int usable = getResources().getDisplayMetrics().widthPixels - 2 * sidePadding;
        int gap = dp(22);

        // Ajustes → Tamanho dos blocos muda as colunas da grade; os favoritos são 20% maiores.
        int columns = Theme.SIZE_COLUMNS[store.tileSize()];
        int gridWidth = (usable - (columns - 1) * gap) / columns;
        int favWidth = gridWidth * 6 / 5;
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


        inputsRow.removeAllViews();
        List<TvInputs.Input> inputs = store.showInputs()
                ? TvInputs.list(this) : new ArrayList<TvInputs.Input>();
        for (int i = 0; i < inputs.size(); i++) {
            final TvInputs.Input input = inputs.get(i);
            Drawable icon = input.icon != null ? input.icon : getDrawableCompat(R.drawable.ic_input);
            View cell = buildCell(TAG_INPUT + input.id, input.label, null, icon, 0, gridWidth,
                    v -> openInput(input), null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = gap;
            inputsRow.addView(cell, lp);
        }
        int inputsVisibility = inputs.isEmpty() ? View.GONE : View.VISIBLE;
        inputsTitle.setVisibility(inputsVisibility);
        inputsScroll.setVisibility(inputsVisibility);
        grid.removeAllViews();
        List<AppEntry> all = store.allVisible();
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % columns == 0) {
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
            if (i % columns > 0) lp.leftMargin = gap;
            row.addView(buildTile(all.get(i), TAG_GRID, gridWidth), lp);
        }

        restoreFocus(focusTag);
    }

    /** Um bloco 16:9 com o banner do app (ou ícone + nome) e o nome embaixo, visível no foco. */
    private View buildTile(AppEntry app, String section, int width) {
        Drawable banner = app.banner(pm);
        // Sem banner: o bloco ganha a cor do próprio ícone, como um cartão do app.
        int fill = banner == null ? app.tileColor(pm) : 0;
        return buildCell(section + app.key, app.label, banner, app.icon(pm), fill, width,
                v -> launch(app, v), () -> showAppOptions(app, section));
    }

    private View buildCell(String tag, String text, Drawable banner, Drawable iconDrawable, int fill, int width,
                           View.OnClickListener onClick, Runnable onOptions) {
        int height = width * 9 / 16;

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(tileBackground(fill));
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setTag(tag);
        tile.setContentDescription(text);

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
            icon.setImageDrawable(iconDrawable);
            int iconSize = height * 45 / 100;
            inner.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            TextView name = new TextView(this);
            name.setText(text);
            name.setTextColor(fill != 0 || !theme.light ? Color.WHITE : theme.text);
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
        label.setText(text);
        label.setTextColor(darkText() ? theme.text : Color.WHITE);
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
        tile.setOnClickListener(onClick);
        if (onOptions != null) {
            tile.setOnLongClickListener(v -> {
                onOptions.run();
                return true;
            });
            // Menu (controles antigos) ou botão vermelho (Sony e outras marcas) abrem as opções.
            tile.setOnKeyListener((v, keyCode, event) -> {
                if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_PROG_RED)
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    onOptions.run();
                    return true;
                }
                return false;
            });
        }
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

    private void openInput(TvInputs.Input input) {
        try {
            startActivity(input.intent());
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.launch_failed, input.label), Toast.LENGTH_SHORT).show();
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
        labels.add(getString(R.string.theme_label, getString(theme.nameRes)));
        actions.add(this::pickTheme);
        labels.add(getString(R.string.size_label, getString(Theme.SIZE_NAMES[store.tileSize()])));
        actions.add(this::pickTileSize);
        labels.add(getString(store.squareCorners() ? R.string.corners_square : R.string.corners_round));
        actions.add(() -> {
            store.toggleCorners();
            refresh(null);
        });
        labels.add(getString(R.string.toggle_inputs));
        actions.add(() -> {
            store.toggleInputs();
            refresh(null);
        });
        labels.add(getString(R.string.toggle_seconds));
        actions.add(() -> {
            store.toggleSeconds();
            applyClockFormat();
        });
        labels.add(getString(R.string.toggle_wallpaper));
        actions.add(() -> {
            store.toggleWallpaper();
            refresh(null);
        });
        labels.add(getString(R.string.reset_layout));
        actions.add(() -> {
            store.resetLayout();
            refresh(null);
        });

        showMenu(getString(R.string.settings), labels, actions);
    }

    /** Lista de temas com o atual marcado; a escolha vale na hora. */
    private void pickTheme() {
        String[] names = new String[Theme.ALL.length];
        int current = 0;
        for (int i = 0; i < Theme.ALL.length; i++) {
            names[i] = getString(Theme.ALL[i].nameRes);
            if (Theme.ALL[i] == theme) current = i;
        }
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(names, current, (dialog, which) -> {
                    store.setTheme(Theme.ALL[which]);
                    dialog.dismiss();
                    refresh(null);
                })
                .show();
    }

    private void pickTileSize() {
        String[] names = new String[Theme.SIZE_NAMES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(Theme.SIZE_NAMES[i]);
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.size_title)
                .setSingleChoiceItems(names, store.tileSize(), (dialog, which) -> {
                    store.setTileSize(which);
                    dialog.dismiss();
                    refresh(null);
                })
                .show();
    }

    private void showMenu(String title, List<String> labels, List<Runnable> actions) {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    /** Diálogos claros no tema claro. Os estilos DeviceDefault de alerta só existem a partir do 5.1. */
    private int dialogTheme() {
        boolean light = theme != null && theme.light;
        if (Build.VERSION.SDK_INT >= 22) {
            return light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Dialog_Alert;
        }
        return light ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert;
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

    /** Cores do tema: fundo (gradiente ou papel de parede da TV com véu), textos e botão de ajustes. */
    private void applyTheme() {
        if (store.useWallpaper()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(null);
            root.setBackgroundResource(R.drawable.scrim);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, new int[] {theme.bgTop, theme.bgBottom}));
            root.setBackground(null);
        }
        boolean darkText = darkText();
        clock.setTextColor(darkText ? theme.text : Color.WHITE);
        for (TextView t : secondaryTexts) t.setTextColor(darkText ? theme.textSecondary : 0xB3FFFFFF);
        settingsButton.setBackground(tileBackground(0));
        settingsButton.setColorFilter(darkText ? theme.text : Color.WHITE);
    }

    /** Texto escuro só no tema claro sem papel de parede (o véu sobre o papel de parede é escuro). */
    private boolean darkText() {
        return theme.light && !store.useWallpaper();
    }

    /** Fundo de um bloco: cor própria (ou a do tema), cantos conforme o ajuste, contorno no foco. */
    private Drawable tileBackground(int fill) {
        int color = fill != 0 ? fill : theme.tile;
        float radius = store.squareCorners() ? dp(3) : dp(14);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color);
        normal.setCornerRadius(radius);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(color);
        focused.setCornerRadius(radius);
        focused.setStroke(dp(3), theme.focus);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[0], normal);
        return states;
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

    private Drawable getDrawableCompat(int id) {
        return getDrawable(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}

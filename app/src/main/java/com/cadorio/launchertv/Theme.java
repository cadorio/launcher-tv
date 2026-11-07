package com.cadorio.launchertv;

/** Os temas visuais que o usuário escolhe em Ajustes → Tema. Mesmos nomes e cores da versão web. */
final class Theme {
    final String id;
    final int nameRes;
    final int bgTop;
    final int bgBottom;
    /** Fundo dos blocos sem banner nem cor de ícone (ex.: o botão de ajustes). */
    final int tile;
    /** Contorno do item em foco. */
    final int focus;
    final int text;
    final int textSecondary;
    /** Tema claro: diálogos e ícone de ajustes escuros. */
    final boolean light;

    private Theme(String id, int nameRes, int bgTop, int bgBottom, int tile, int focus,
                  int text, int textSecondary, boolean light) {
        this.id = id;
        this.nameRes = nameRes;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.tile = tile;
        this.focus = focus;
        this.text = text;
        this.textSecondary = textSecondary;
        this.light = light;
    }

    static final Theme[] ALL = {
            new Theme("graphite", R.string.theme_graphite, 0xFF1B2433, 0xFF07090D, 0x33FFFFFF, 0xFFFFFFFF,
                    0xFFFFFFFF, 0xB3FFFFFF, false),
            new Theme("oled", R.string.theme_oled, 0xFF000000, 0xFF000000, 0xFF1C1C1E, 0xFFFFFFFF,
                    0xFFFFFFFF, 0x99FFFFFF, false),
            new Theme("ocean", R.string.theme_ocean, 0xFF0B3A5C, 0xFF041320, 0x26FFFFFF, 0xFF7FD6FF,
                    0xFFFFFFFF, 0xB3E0F4FF, false),
            new Theme("aurora", R.string.theme_aurora, 0xFF2A1446, 0xFF0B1A2E, 0x29FFFFFF, 0xFFC9A7FF,
                    0xFFFFFFFF, 0xB3EDE2FF, false),
            new Theme("forest", R.string.theme_forest, 0xFF12372A, 0xFF06120D, 0x26FFFFFF, 0xFF9BE3B0,
                    0xFFFFFFFF, 0xB3E2F5E8, false),
            new Theme("light", R.string.theme_light, 0xFFEEF1F5, 0xFFCFD6DF, 0x14000000, 0xFF1B2433,
                    0xFF111111, 0x99111111, true),
    };

    static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    /** Tamanhos dos blocos: quantas colunas a grade "Todos os apps" tem. */
    static final int[] SIZE_COLUMNS = {6, 5, 4};
    static final int[] SIZE_NAMES = {R.string.size_small, R.string.size_medium, R.string.size_large};
}

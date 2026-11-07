/*
 * Launcher TV para Samsung (Tizen).
 * ES5 de propósito: TVs de 2017-2019 (Tizen 3 a 5) rodam Chromium 47-63.
 * Fora da TV (num navegador comum) roda com uma lista de apps de exemplo, para testar o layout.
 */
(function () {
    'use strict';

    var KEY = {
        LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13,
        BACK: 10009, ESC: 27, BACKSPACE: 8,
        RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406
    };
    var LONG_PRESS_MS = 700;
    var ROW_WIDTH = 1696;       // 1920 menos as margens laterais de 112px
    var STORE = 'ltv.';

    // Apps de streaming que viram favoritos na primeira execução, se instalados.
    // No Tizen os IDs mudam por região, então a busca é pelo nome.
    var DEFAULT_FAVORITES = [/netflix/i, /youtube$/i, /prime video/i, /disney/i, /^max$|hbo/i,
        /globoplay/i, /apple tv/i, /spotify/i];
    var MAX_DEFAULT_FAVORITES = 6;

    var TEXT = {
        pt: {
            favorites: 'Favoritos',
            allApps: 'Todos os apps',
            emptyFavorites: 'Segure OK (ou aperte o botão vermelho) num app para fixá-lo aqui.',
            hint: 'OK abre · segure OK ou ● vermelho: opções · ● verde: ajustes · Voltar: topo / sair',
            settings: 'Ajustes',
            addFavorite: 'Fixar nos favoritos',
            removeFavorite: 'Remover dos favoritos',
            moveLeft: 'Mover para a esquerda',
            moveRight: 'Mover para a direita',
            hide: 'Ocultar app',
            showHidden: 'Mostrar apps ocultos (%d)',
            seconds: 'Relógio: mostrar/ocultar segundos',
            reset: 'Restaurar organização padrão',
            exit: 'Sair do Launcher TV',
            launchFailed: 'Não foi possível abrir %s',
            hidden: '%s foi ocultado. Ajustes → Mostrar apps ocultos traz de volta.',
            loadFailed: 'Não foi possível listar os apps desta TV.'
        },
        en: {
            favorites: 'Favorites',
            allApps: 'All apps',
            emptyFavorites: 'Hold OK (or press the red button) on an app to pin it here.',
            hint: 'OK opens · hold OK or ● red: options · ● green: settings · Back: top / exit',
            settings: 'Settings',
            addFavorite: 'Pin to favorites',
            removeFavorite: 'Remove from favorites',
            moveLeft: 'Move left',
            moveRight: 'Move right',
            hide: 'Hide app',
            showHidden: 'Show hidden apps (%d)',
            seconds: 'Clock: show/hide seconds',
            reset: 'Reset to default layout',
            exit: 'Exit Launcher TV',
            launchFailed: 'Could not open %s',
            hidden: '%s is hidden. Settings → Show hidden apps brings it back.',
            loadFailed: 'Could not list the apps on this TV.'
        }
    };

    var lang = (navigator.language || 'pt').toLowerCase().indexOf('en') === 0 ? 'en' : 'pt';
    var hasTizen = typeof window.tizen !== 'undefined';

    var apps = [];              // [{id, name, icon}], ordem alfabética
    var menuItems = null;       // ações do menu aberto, ou null
    var focused = null;
    var enterTimer = null;
    var enterLongFired = false;
    var enterDown = false;
    var toastTimer = null;

    var $ = function (id) { return document.getElementById(id); };

    function t(key, arg) {
        var s = TEXT[lang][key];
        return arg === undefined ? s : s.replace(/%[sd]/, arg);
    }

    // ------------------------------------------------------------ armazenamento

    function load(key, fallback) {
        try {
            var raw = localStorage.getItem(STORE + key);
            return raw === null ? fallback : JSON.parse(raw);
        } catch (e) {
            return fallback;
        }
    }

    function save(key, value) {
        try {
            localStorage.setItem(STORE + key, JSON.stringify(value));
        } catch (e) { /* armazenamento cheio ou bloqueado: segue sem salvar */ }
    }

    function favoriteIds() { return load('favorites', []); }
    function hiddenIds() { return load('hidden', []); }
    function contains(list, x) { return list.indexOf(x) >= 0; }

    function seedFavorites() {
        var picked = [];
        DEFAULT_FAVORITES.forEach(function (re) {
            if (picked.length >= MAX_DEFAULT_FAVORITES) return;
            for (var i = 0; i < apps.length; i++) {
                if (re.test(apps[i].name) && !contains(picked, apps[i].id)) {
                    picked.push(apps[i].id);
                    return;
                }
            }
        });
        save('favorites', picked);
        save('initialised', true);
    }

    // ------------------------------------------------------------ lista de apps

    var SAMPLE_APPS = ['Netflix', 'YouTube', 'Prime Video', 'Disney+', 'Globoplay', 'Max', 'Spotify',
        'Apple TV', 'Plex', 'Navegador', 'Galeria', 'SmartThings', 'Twitch', 'Pluto TV', 'Deezer'];

    function loadApps(done) {
        if (!hasTizen) {
            done(SAMPLE_APPS.map(function (name, i) { return { id: 'demo.' + i, name: name, icon: '' }; }));
            return;
        }
        var selfId = '';
        try { selfId = tizen.application.getCurrentApplication().appInfo.id; } catch (e) { /* ignora */ }
        try {
            tizen.application.getAppsInfo(function (list) {
                var out = [];
                list.forEach(function (a) {
                    if (!a.show || a.id === selfId || !a.name) return;
                    out.push({ id: a.id, name: String(a.name).trim(), icon: a.iconPath || '' });
                });
                done(out);
            }, function () {
                toast(t('loadFailed'));
                done([]);
            });
        } catch (e) {
            toast(t('loadFailed'));
            done([]);
        }
    }

    function refresh(focusKey) {
        loadApps(function (list) {
            list.sort(function (a, b) { return a.name.localeCompare(b.name, lang, { sensitivity: 'base' }); });
            apps = list;
            if (!load('initialised', false)) seedFavorites();
            render(focusKey || (focused && focused.getAttribute('data-key')));
        });
    }

    function byId(id) {
        for (var i = 0; i < apps.length; i++) if (apps[i].id === id) return apps[i];
        return null;
    }

    function visibleFavorites() {
        var hidden = hiddenIds();
        return favoriteIds().map(byId).filter(function (a) { return a && !contains(hidden, a.id); });
    }

    // ------------------------------------------------------------ desenho

    function tile(app, section) {
        var el = document.createElement('div');
        el.className = 'tile focusable';
        el.setAttribute('data-key', section + ':' + app.id);
        el.setAttribute('data-section', section);
        el.setAttribute('data-app', app.id);

        if (app.icon) {
            var img = document.createElement('img');
            img.src = app.icon;
            img.alt = '';
            img.onerror = function () { img.parentNode && img.parentNode.removeChild(img); };
            el.appendChild(img);
        }
        var name = document.createElement('div');
        name.className = 'name';
        name.textContent = app.name;
        el.appendChild(name);
        return el;
    }

    function render(focusKey) {
        var favBox = $('favorites');
        var grid = $('grid');
        favBox.innerHTML = '';
        grid.innerHTML = '';

        var favs = visibleFavorites();
        favs.forEach(function (a) { favBox.appendChild(tile(a, 'fav')); });
        $('empty-favorites').hidden = favs.length > 0;

        var hidden = hiddenIds();
        apps.forEach(function (a) {
            if (!contains(hidden, a.id)) grid.appendChild(tile(a, 'all'));
        });

        var target = focusKey && document.querySelector('[data-key="' + cssEscape(focusKey) + '"]');
        setFocus(target || firstTile());
    }

    function cssEscape(s) { return String(s).replace(/["\\]/g, '\\$&'); }

    function firstTile() {
        return document.querySelector('#favorites .tile') || document.querySelector('#grid .tile') || $('settings');
    }

    // ------------------------------------------------------------ foco e navegação

    function setFocus(el) {
        if (!el) return;
        if (focused) focused.classList.remove('focused');
        focused = el;
        el.classList.add('focused');
        if (!menuItems) keepVisible(el);
    }

    /** Rola a página para o item em foco não ficar escondido sob o rodapé ou acima da tela. */
    function keepVisible(el) {
        var page = $('page');
        if (el.parentNode.id === 'favorites') {
            // A fileira de favoritos é mais larga que a tela: desliza para o lado.
            var row = el.parentNode;
            var shift = Math.max(0, el.offsetLeft + el.offsetWidth - ROW_WIDTH);
            row.style.webkitTransform = row.style.transform = 'translateX(' + (-shift) + 'px)';
        }
        if (el.id === 'settings' || el.parentNode.id === 'favorites') {
            page.scrollTop = 0;
            return;
        }
        var r = el.getBoundingClientRect();
        var top = 120, bottom = 1080 - 150;
        if (r.top < top) page.scrollTop -= top - r.top;
        else if (r.bottom > bottom) page.scrollTop += r.bottom - bottom;
    }

    /** Navegação espacial: vai para o item mais próximo na direção da seta. */
    function move(dir) {
        var scope = menuItems ? $('menu') : $('page');
        var items = scope.querySelectorAll('.focusable');
        if (!focused) { setFocus(items[0]); return; }

        var from = focused.getBoundingClientRect();
        var fx = from.left + from.width / 2, fy = from.top + from.height / 2;
        var best = null, bestScore = Infinity;

        for (var i = 0; i < items.length; i++) {
            var el = items[i];
            if (el === focused) continue;
            var r = el.getBoundingClientRect();
            var dx = r.left + r.width / 2 - fx, dy = r.top + r.height / 2 - fy;
            var main, side;
            if (dir === 'left') { main = -dx; side = dy; }
            else if (dir === 'right') { main = dx; side = dy; }
            else if (dir === 'up') { main = -dy; side = dx; }
            else { main = dy; side = dx; }
            if (main <= 1) continue;
            var score = main + Math.abs(side) * 3;
            if (score < bestScore) { bestScore = score; best = el; }
        }
        if (best) setFocus(best);
    }

    function goTop() {
        $('page').scrollTop = 0;
        setFocus(firstTile());
    }

    // ------------------------------------------------------------ ações

    function activate(el) {
        if (!el) return;
        if (menuItems) {
            var action = menuItems[Number(el.getAttribute('data-index'))];
            closeMenu();
            action.run();
        } else if (el.id === 'settings') {
            showSettings();
        } else if (el.getAttribute('data-app')) {
            launch(byId(el.getAttribute('data-app')));
        }
    }

    function launch(app) {
        if (!app) return;
        if (!hasTizen) { toast('▶ ' + app.name); return; }
        try {
            tizen.application.launch(app.id, null, function () { toast(t('launchFailed', app.name)); });
        } catch (e) {
            toast(t('launchFailed', app.name));
        }
    }

    function showAppOptions(el) {
        var id = el && el.getAttribute('data-app');
        var app = id && byId(id);
        if (!app) return;
        var section = el.getAttribute('data-section');
        var items = [];

        if (contains(favoriteIds(), app.id)) {
            if (section === 'fav') {
                items.push({ label: t('moveLeft'), run: function () { moveFavorite(app, -1); } });
                items.push({ label: t('moveRight'), run: function () { moveFavorite(app, 1); } });
            }
            items.push({ label: t('removeFavorite'), run: function () {
                save('favorites', favoriteIds().filter(function (x) { return x !== app.id; }));
                render('all:' + app.id);
            } });
        } else {
            items.push({ label: t('addFavorite'), run: function () {
                var f = favoriteIds();
                f.push(app.id);
                save('favorites', f);
                render('fav:' + app.id);
            } });
        }
        items.push({ label: t('hide'), run: function () {
            var h = hiddenIds();
            if (!contains(h, app.id)) h.push(app.id);
            save('hidden', h);
            toast(t('hidden', app.name));
            render(null);
        } });
        openMenu(app.name, items);
    }

    /** Troca o app de lugar com o vizinho visível; favoritos ocultos ou desinstalados ficam onde estão. */
    function moveFavorite(app, delta) {
        var visible = visibleFavorites();
        var from = -1;
        for (var i = 0; i < visible.length; i++) if (visible[i].id === app.id) from = i;
        var to = from + delta;
        if (from < 0 || to < 0 || to >= visible.length) { render('fav:' + app.id); return; }

        var ids = favoriteIds();
        var a = ids.indexOf(visible[from].id), b = ids.indexOf(visible[to].id);
        ids[a] = visible[to].id;
        ids[b] = visible[from].id;
        save('favorites', ids);
        render('fav:' + app.id);
    }

    function showSettings() {
        var items = [];
        var hiddenCount = hiddenIds().filter(byId).length;
        if (hiddenCount > 0) {
            items.push({ label: t('showHidden', hiddenCount), run: function () { save('hidden', []); render(null); } });
        }
        items.push({ label: t('seconds'), run: function () { save('seconds', !load('seconds', false)); tick(); } });
        items.push({ label: t('reset'), run: function () {
            save('hidden', []);
            seedFavorites();
            render(null);
        } });
        if (hasTizen) items.push({ label: t('exit'), run: exitApp });
        openMenu(t('settings'), items);
    }

    function openMenu(title, items) {
        menuItems = items;
        menuReturn = focused;
        $('menu-title').textContent = title;
        var box = $('menu-items');
        box.innerHTML = '';
        items.forEach(function (item, i) {
            var el = document.createElement('div');
            el.className = 'menu-item focusable';
            el.setAttribute('data-index', i);
            el.textContent = item.label;
            box.appendChild(el);
        });
        $('menu').hidden = false;
        setFocus(box.firstChild);
    }

    var menuReturn = null;

    function closeMenu() {
        menuItems = null;
        $('menu').hidden = true;
        setFocus(menuReturn && document.body.contains(menuReturn) ? menuReturn : firstTile());
    }

    function exitApp() {
        try { tizen.application.getCurrentApplication().exit(); } catch (e) { /* fora da TV */ }
    }

    function back() {
        if (menuItems) { closeMenu(); return; }
        if (focused !== firstTile() || $('page').scrollTop > 0) { goTop(); return; }
        exitApp();
    }

    function toast(msg) {
        var el = $('toast');
        el.textContent = msg;
        el.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.hidden = true; }, 3500);
    }

    // ------------------------------------------------------------ teclas

    function onKeyDown(e) {
        switch (e.keyCode) {
            case KEY.LEFT: move('left'); break;
            case KEY.RIGHT: move('right'); break;
            case KEY.UP: move('up'); break;
            case KEY.DOWN: move('down'); break;
            case KEY.ENTER:
                // Segurar OK abre as opções do app; um toque rápido abre o app.
                if (!enterDown) {
                    enterDown = true;
                    enterLongFired = false;
                    var target = focused;
                    enterTimer = setTimeout(function () {
                        if (!menuItems && target && target.getAttribute('data-app')) {
                            enterLongFired = true;
                            showAppOptions(target);
                        }
                    }, LONG_PRESS_MS);
                }
                break;
            case KEY.BACK: case KEY.ESC: case KEY.BACKSPACE: back(); break;
            case KEY.RED: case 77 /* M, no navegador */:
                if (!menuItems) showAppOptions(focused);
                break;
            case KEY.GREEN: case 83 /* S, no navegador */:
                if (!menuItems) showSettings();
                break;
            default: return;
        }
        e.preventDefault();
    }

    function onKeyUp(e) {
        if (e.keyCode !== KEY.ENTER) return;
        clearTimeout(enterTimer);
        var wasLong = enterLongFired;
        enterDown = false;
        enterLongFired = false;
        if (!wasLong) activate(focused);
        e.preventDefault();
    }

    function registerKeys() {
        if (!hasTizen || !tizen.tvinputdevice) return;
        ['ColorF0Red', 'ColorF1Green'].forEach(function (k) {
            try { tizen.tvinputdevice.registerKey(k); } catch (e) { /* tecla indisponível */ }
        });
    }

    // ------------------------------------------------------------ relógio

    function pad(n) { return n < 10 ? '0' + n : String(n); }

    function tick() {
        var d = new Date();
        var time = pad(d.getHours()) + ':' + pad(d.getMinutes());
        if (load('seconds', false)) time += ':' + pad(d.getSeconds());
        $('clock').textContent = time;
        var date;
        try {
            date = d.toLocaleDateString(lang === 'en' ? 'en-US' : 'pt-BR',
                { weekday: 'long', day: 'numeric', month: 'long' });
        } catch (e) {
            date = d.toDateString();
        }
        $('date').textContent = date.charAt(0).toUpperCase() + date.slice(1);
    }

    // ------------------------------------------------------------ início

    function init() {
        var labels = document.querySelectorAll('[data-i18n]');
        for (var i = 0; i < labels.length; i++) labels[i].textContent = t(labels[i].getAttribute('data-i18n'));
        $('settings').setAttribute('aria-label', t('settings'));

        registerKeys();
        document.addEventListener('keydown', onKeyDown);
        document.addEventListener('keyup', onKeyUp);
        // Ao voltar de outro app, relê a lista (algo pode ter sido instalado ou removido).
        document.addEventListener('visibilitychange', function () { if (!document.hidden) refresh(null); });

        tick();
        setInterval(tick, 1000);
        refresh(null);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();

/*
 * Adaptador LG webOS (TVs 2014+). Contrato em web/js/platform.js. ES5 (webOS 3 usa Chromium 38).
 *
 * Apps de desenvolvedor não podem, em geral, listar os apps instalados (listApps é restrito).
 * Por isso tentamos listApps e, se for negado, usamos um catálogo dos apps mais comuns na LG.
 * O launcher oculta sozinho um app do catálogo que não abrir (provavelmente não instalado).
 *
 * Ícones: cada app da LG tem uma pasta com appinfo.json, que diz qual é o ícone. Lemos esse
 * arquivo direto do disco da TV (os apps de desenvolvedor rodam em file://). Se der para ler,
 * o catálogo também fica exato: só aparecem os apps cuja pasta existe.
 */
(function () {
    'use strict';

    var CATALOG = [
        ['netflix', 'Netflix'],
        ['youtube.leanback.v4', 'YouTube'],
        ['amazon', 'Prime Video'],
        ['com.disney.disneyplus-prod', 'Disney+'],
        ['com.apple.appletv', 'Apple TV'],
        ['spotify-beehive', 'Spotify'],
        ['com.webos.app.livetv', 'TV ao vivo'],
        ['com.webos.app.hdmi1', 'HDMI 1'],
        ['com.webos.app.hdmi2', 'HDMI 2'],
        ['com.webos.app.hdmi3', 'HDMI 3'],
        ['com.webos.app.hdmi4', 'HDMI 4'],
        ['com.webos.app.browser', 'Navegador'],
        ['com.webos.app.discovery', 'LG Content Store'],
        ['com.palm.app.settings', 'Configurações']
    ];

    /** Chamada a um serviço Luna, como o webOSTV.js faz por baixo. */
    function luna(uri, params, done) {
        if (typeof window.PalmServiceBridge === 'undefined') { done({ returnValue: false }); return; }
        var bridge = new window.PalmServiceBridge();
        var finished = false;
        bridge.onservicecallback = function (msg) {
            if (finished) return;
            finished = true;
            var res;
            try { res = JSON.parse(msg); } catch (e) { res = { returnValue: false }; }
            done(res);
        };
        bridge.call(uri, JSON.stringify(params || {}));
    }

    var SELF_ID = 'com.cadorio.launchertv'; // igual ao "id" do appinfo.json

    // Apps de fábrica ficam na primeira pasta; os baixados da LG Content Store, na segunda.
    var APP_ROOTS = ['/usr/palm/applications/', '/media/cryptofs/apps/usr/palm/applications/'];
    var PROBE_TIMEOUT_MS = 2500;

    /** Lê um arquivo local; done(texto) ou done(null). */
    function readFile(path, done) {
        var xhr = new XMLHttpRequest();
        var finished = false;
        function end(text) { if (!finished) { finished = true; done(text); } }
        try {
            xhr.open('GET', 'file://' + path, true);
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) return;
                // file:// responde com status 0 quando dá certo.
                end((xhr.status === 0 || xhr.status === 200) && xhr.responseText ? xhr.responseText : null);
            };
            xhr.onerror = function () { end(null); };
            xhr.send();
        } catch (e) {
            end(null);
        }
    }

    /** Procura o appinfo.json do app nas pastas da TV; done({dir, info}) ou done(null). */
    function findAppInfo(id, done) {
        var i = 0;
        (function next() {
            if (i >= APP_ROOTS.length) { done(null); return; }
            var dir = APP_ROOTS[i++] + id + '/';
            readFile(dir + 'appinfo.json', function (text) {
                var info = null;
                try { info = text && JSON.parse(text); } catch (e) { info = null; }
                if (info) done({ dir: dir, info: info });
                else next();
            });
        })();
    }

    /** Caminho absoluto do ícone; prefere o grande. */
    function iconPath(dir, info) {
        var f = (info && (info.largeIcon || info.icon)) || '';
        if (!f) return '';
        if (/^(file:|https?:)/.test(f)) return f;
        return f.charAt(0) === '/' ? 'file://' + f : 'file://' + dir + f;
    }

    /**
     * Completa o catálogo com o que der para ler da TV: ícone real e se o app existe.
     * done(apps, catalog): catalog=false quando a leitura funcionou (a lista é exata).
     */
    function probeCatalog(done) {
        var entries = CATALOG.map(function (c) { return { id: c[0], name: c[1], icon: '' }; });
        var pending = entries.length;
        var found = 0;
        var finished = false;
        var installed = {};

        function finish() {
            if (finished) return;
            finished = true;
            if (found === 0) { done(entries, true); return; } // sem acesso ao disco: catálogo puro
            done(entries.filter(function (e) { return installed[e.id]; }), false);
        }
        setTimeout(finish, PROBE_TIMEOUT_MS);

        entries.forEach(function (e) {
            findAppInfo(e.id, function (res) {
                if (res) {
                    found++;
                    installed[e.id] = true;
                    e.icon = iconPath(res.dir, res.info);
                }
                if (--pending === 0) finish();
            });
        });
    }

    window.LauncherPlatform = {
        name: 'webos',
        keys: { back: [461], red: [403], green: [404] },
        init: function () {},

        listApps: function (done) {
            luna('luna://com.webos.applicationManager/listApps', {}, function (res) {
                if (res && res.returnValue && res.apps && res.apps.length) {
                    var out = [];
                    res.apps.forEach(function (a) {
                        if (a.visible === false || a.id === SELF_ID || !a.title) return;
                        var dir = a.folderPath ? a.folderPath.replace(/\/?$/, '/') : '';
                        out.push({ id: a.id, name: String(a.title).trim(), icon: iconPath(dir, a) });
                    });
                    done(out, false);
                    return;
                }
                probeCatalog(done);
            });
        },

        launch: function (app, fail) {
            luna('luna://com.webos.applicationManager/launch', { id: app.id }, function (res) {
                if (!res || !res.returnValue) fail((res && res.errorText) || '');
            });
        },

        exit: function () { window.close(); }
    };
})();

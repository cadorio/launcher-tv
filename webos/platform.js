/*
 * Adaptador LG webOS (TVs 2014+). Contrato em web/js/platform.js. ES5 (webOS 3 usa Chromium 38).
 *
 * Apps de desenvolvedor não podem, em geral, listar os apps instalados (listApps é restrito).
 * Por isso tentamos listApps e, se for negado, usamos um catálogo dos apps mais comuns na LG.
 * O launcher oculta sozinho um app do catálogo que não abrir (provavelmente não instalado).
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
                        out.push({ id: a.id, name: String(a.title).trim(), icon: a.icon || '' });
                    });
                    done(out, false);
                    return;
                }
                done(CATALOG.map(function (c) { return { id: c[0], name: c[1], icon: '' }; }), true);
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

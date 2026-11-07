/*
 * Adaptador de demonstração: usado ao abrir web/index.html num navegador comum.
 * Os pacotes de TV substituem este arquivo pelo adaptador da plataforma:
 * tizen/platform.js (Samsung) ou webos/platform.js (LG).
 *
 * Contrato que o launcher espera em window.LauncherPlatform:
 *   name              nome da plataforma
 *   keys              {back: [...], red: [...], green: [...]} códigos de tecla extras
 *   init()            preparação (registrar teclas etc.)
 *   listApps(done)    chama done(apps, catalog); apps = [{id, name, icon}];
 *                     catalog = true quando a lista é um catálogo fixo (pode ter apps não instalados)
 *   launch(app, fail) abre o app; chama fail(motivo) se não conseguir
 *   exit              função que fecha o launcher, ou null
 */
(function () {
    'use strict';
    var SAMPLE = ['Netflix', 'YouTube', 'Prime Video', 'Disney+', 'Globoplay', 'Max', 'Spotify',
        'Apple TV', 'Plex', 'Navegador', 'Galeria', 'HDMI 1', 'HDMI 2', 'Twitch', 'Pluto TV', 'Deezer'];

    window.LauncherPlatform = {
        name: 'demo',
        keys: { back: [27, 8], red: [77], green: [83] }, // Esc/Backspace, M, S
        init: function () {},
        listApps: function (done) {
            done(SAMPLE.map(function (name, i) { return { id: 'demo.' + i, name: name, icon: '' }; }), false);
        },
        launch: function (app, fail) {
            if (/^Twitch$/.test(app.name)) fail('demo: falha simulada');
            else window.LauncherPlatform.lastLaunched = app.id;
        },
        exit: null
    };
})();

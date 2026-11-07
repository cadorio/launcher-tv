/* Adaptador Samsung Tizen (TVs 2015+). Contrato em web/js/platform.js. ES5. */
(function () {
    'use strict';

    function current() {
        try { return tizen.application.getCurrentApplication(); } catch (e) { return null; }
    }

    window.LauncherPlatform = {
        name: 'tizen',
        keys: { back: [10009], red: [403], green: [404] },

        init: function () {
            if (!window.tizen || !tizen.tvinputdevice) return;
            ['ColorF0Red', 'ColorF1Green'].forEach(function (k) {
                try { tizen.tvinputdevice.registerKey(k); } catch (e) { /* tecla indisponível */ }
            });
        },

        listApps: function (done) {
            var app = current();
            var selfId = app ? app.appInfo.id : '';
            try {
                tizen.application.getAppsInfo(function (list) {
                    var out = [];
                    list.forEach(function (a) {
                        // show=false: serviços e apps internos que a própria Samsung esconde.
                        if (!a.show || a.id === selfId || !a.name) return;
                        out.push({ id: a.id, name: String(a.name).trim(), icon: a.iconPath || '' });
                    });
                    done(out, false);
                }, function () { done(null, false); });
            } catch (e) {
                done(null, false);
            }
        },

        launch: function (app, fail) {
            try {
                tizen.application.launch(app.id, null, function (err) { fail(err && err.message); });
            } catch (e) {
                fail(e.message);
            }
        },

        exit: function () {
            var app = current();
            if (app) app.exit();
        }
    };
})();

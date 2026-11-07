#!/usr/bin/env bash
# Empacota o Launcher TV para LG (webOS) e instala na TV pela rede.
# Precisa do CLI da LG ("ares"): npm install -g @webos-tools/cli
# A TV precisa do app "Developer Mode" ligado; passo a passo em webos/README.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB="$HERE/../web"
OUT="$HERE/build"
APP_ID="com.cadorio.launchertv"
DEVICE="${LGTV_DEVICE:-lgtv}"

die() { echo "erro: $*" >&2; exit 1; }

need() {
    command -v "$1" >/dev/null || die "\"$1\" não encontrado. Instale o CLI da LG: npm install -g @webos-tools/cli"
}

cmd_stage() {
    local stage="$OUT/stage"
    rm -rf "$OUT" && mkdir -p "$stage"
    # Interface comum (web/) + o adaptador LG no lugar do de demonstração.
    cp -r "$WEB/index.html" "$WEB/css" "$WEB/js" "$HERE/appinfo.json" "$HERE/icon.png" "$HERE/largeIcon.png" "$stage/"
    cp "$HERE/platform.js" "$stage/js/platform.js"
    echo "$stage"
}

cmd_package() {
    need ares-package
    local stage; stage=$(cmd_stage)
    ares-package --no-minify -o "$OUT" "$stage"
    ls "$OUT"/*.ipk
}

# Cadastra a TV no ares (uma vez). A senha (passphrase) aparece no app Developer Mode da TV.
setup_device() {
    local ip="$1"
    need ares-setup-device
    if ares-setup-device --list 2>/dev/null | grep -q "^$DEVICE "; then
        ares-setup-device -m "$DEVICE" -i "host=$ip" -i "port=9922" -i "username=prisoner" >/dev/null
    else
        ares-setup-device -a "$DEVICE" -i "host=$ip" -i "port=9922" -i "username=prisoner"
    fi
    echo "Digite a Passphrase mostrada no app Developer Mode da TV:"
    ares-novacom --device "$DEVICE" --getkey
}

cmd_install() {
    [[ $# -ge 1 ]] || die "uso: $0 install <ip-da-tv>"
    need ares-install
    setup_device "$1"
    local ipk; ipk=$(cmd_package | tail -n 1)
    ares-install --device "$DEVICE" "$ipk"
    ares-launch --device "$DEVICE" "$APP_ID" || true
    echo "Instalado. O Launcher TV aparece na barra de apps da TV."
}

cmd_preview() {
    echo "Abra no navegador (modo demonstração, setas + Enter; M = opções, S = ajustes, Esc = voltar):"
    echo "  file://$(cd "$WEB" && pwd)/index.html"
}

case "${1:-}" in
    stage) cmd_stage ;;
    package) cmd_package ;;
    install) shift; cmd_install "$@" ;;
    preview) cmd_preview ;;
    *) echo "Uso: $0 package | install <ip-da-tv> | preview | stage"; exit 1 ;;
esac

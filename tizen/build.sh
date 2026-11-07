#!/usr/bin/env bash
# Empacota o Launcher TV para Samsung (Tizen) e instala na TV pela rede.
# Precisa do Tizen Studio (CLI "tizen" e "sdb") e de um perfil de certificado Samsung;
# o passo a passo está em tizen/README.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB="$HERE/../web"
OUT="$HERE/build"
WGT="$OUT/LauncherTV.wgt"
APP_ID="LnchrTV001.LauncherTV"
PROFILE="${TIZEN_PROFILE:-launchertv}"

die() { echo "erro: $*" >&2; exit 1; }

# Acha o CLI no PATH ou na instalação padrão do Tizen Studio.
find_tool() {
    local name="$1" dir
    if command -v "$name" >/dev/null; then command -v "$name"; return; fi
    for dir in "$HOME/tizen-studio/tools/ide/bin" "$HOME/tizen-studio/tools" \
               "/c/tizen-studio/tools/ide/bin" "/c/tizen-studio/tools"; do
        [[ -x "$dir/$name" ]] && { echo "$dir/$name"; return; }
    done
    die "\"$name\" não encontrado. Instale o Tizen Studio (veja tizen/README.md)."
}

cmd_stage() {
    local stage="$OUT/stage"
    rm -rf "$OUT" && mkdir -p "$stage"
    # Interface comum (web/) + o adaptador Samsung no lugar do de demonstração.
    cp -r "$WEB/index.html" "$WEB/css" "$WEB/js" "$HERE/config.xml" "$HERE/icon.png" "$stage/"
    cp "$HERE/platform.js" "$stage/js/platform.js"
    echo "$stage"
}

cmd_package() {
    local tizen; tizen=$(find_tool tizen)
    local stage; stage=$(cmd_stage)
    "$tizen" package -t wgt -s "$PROFILE" -o "$OUT" -- "$stage" ||
        die "falha ao assinar. O perfil de certificado \"$PROFILE\" existe? (TIZEN_PROFILE=<nome> para usar outro)"
    local made; made=$(ls "$OUT"/*.wgt | head -n 1)
    [[ "$made" == "$WGT" ]] || mv "$made" "$WGT"
    echo "Pacote: $WGT"
}

cmd_install() {
    [[ $# -ge 1 ]] || die "uso: $0 install <ip-da-tv>"
    local target="$1"
    [[ "$target" == *:* ]] || target="$target:26101"
    [[ -f "$WGT" ]] || cmd_package
    local sdb tizen; sdb=$(find_tool sdb); tizen=$(find_tool tizen)
    echo "Conectando a $target (a TV precisa estar em Modo Desenvolvedor com o IP deste computador)…"
    "$sdb" connect "$target"
    "$tizen" install -n "$(basename "$WGT")" -s "$target" -- "$OUT"
    "$tizen" run -p "$APP_ID" -s "$target" || true
    echo "Instalado. O Launcher TV aparece em Apps na TV."
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

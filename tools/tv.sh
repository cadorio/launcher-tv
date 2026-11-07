#!/usr/bin/env bash
# Launcher TV: limpa uma TV Android / Google TV pela rede, via ADB.
# Nada é desinstalado: os apps são desativados com "pm disable-user" e tudo
# fica registrado em backup/<serial>/ para o comando "restore" desfazer.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LISTS="$ROOT/tools/packages"
OUR_PKG="com.cadorio.launchertv"
OUR_HOME="$OUR_PKG/.HomeActivity"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"

# Nunca desativar: sem eles a TV perde imagem, controle, loja ou ajustes.
PROTECTED="^($OUR_PKG|android|com\.android\.(systemui|settings|tv\.settings|providers\..*|vending|packageinstaller|inputmethod\..*)|com\.google\.android\.(gms|gsf|tv\.remote\.service|tvsetup|tungsten\.setupwraith)|com\.sony\.dtv\.(tvx|tvinput\..*|settings.*|osat.*|sonysystemservice))$"

die() { echo "erro: $*" >&2; exit 1; }
say() { printf '\033[1m%s\033[0m\n' "$*"; }

need_adb() { command -v adb >/dev/null || die "adb não encontrado. Instale o Android platform-tools."; }

# Escolhe a TV: variável TV=ip:porta, ou o único dispositivo conectado.
serial() {
    if [[ -n "${TV:-}" ]]; then echo "$TV"; return; fi
    local devs
    devs=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    [[ -z "$devs" ]] && die "nenhuma TV conectada. Rode: $0 connect <ip-da-tv>"
    [[ $(wc -l <<<"$devs") -gt 1 ]] && die "mais de uma TV conectada; escolha com TV=<serial> $0 ..."
    echo "$devs"
}

# </dev/null: senão o adb consome a entrada dos loops "while read".
sh_tv() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }

backup_dir() {
    local dir="$ROOT/backup/${SERIAL//[:\/]/_}"
    mkdir -p "$dir"
    echo "$dir"
}

sdk() { sh_tv getprop ro.build.version.sdk; }

# "cmd package" só existe no Android 7+ (SDK 24). Nas Sony de 2015-2016 (Android 5/6) não há como
# consultar ou trocar a tela inicial pela linha de comando: devolve vazio.
current_home() {
    [[ "$(sdk)" -ge 24 ]] || return 0
    sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1
}

our_home_active() {
    local home; home=$(current_home)
    if [[ -n "$home" ]]; then [[ "$home" == "$OUR_PKG/"* ]]; else is_installed "$OUR_PKG"; fi
}

is_installed() { sh_tv pm list packages "$1" | grep -qx "package:$1"; }

brand() { sh_tv getprop ro.product.manufacturer | tr '[:upper:]' '[:lower:]'; }

is_enabled() { sh_tv pm list packages -e "$1" | grep -qx "package:$1"; }

ask() {
    local reply
    read -r -p "$1 [s/N] " reply || true
    [[ "$reply" =~ ^[sSyY]$ ]]
}

cmd_info() {
    local b; b=$(brand)
    echo "Marca:    $b"
    echo "Modelo:   $(sh_tv getprop ro.product.model)"
    echo "Android:  $(sh_tv getprop ro.build.version.release) (SDK $(sdk))"
    echo "Início:   $(current_home || true)"
    if [[ -f "$LISTS/$b.txt" ]]; then
        echo "Lista da marca: tools/packages/$b.txt (usada automaticamente pelo debloat)"
    fi
    if [[ "$(sdk)" -lt 21 ]]; then
        echo "Atenção: o Launcher TV precisa de Android 5.0 ou mais novo."
    fi
}

cmd_connect() {
    [[ $# -ge 1 ]] || die "uso: $0 connect <ip-da-tv>[:porta]"
    local target="$1"
    [[ "$target" == *:* ]] || target="$target:5555"
    say "Conectando a $target. Aceite \"Permitir depuração\" na TV se ela perguntar."
    adb connect "$target"
    adb -s "$target" wait-for-device
    echo "Conectado. Modelo: $(adb -s "$target" shell getprop ro.product.model | tr -d '\r')"
}

cmd_backup() {
    local dir; dir=$(backup_dir)
    local stamp; stamp=$(date +%Y%m%d-%H%M%S)
    sh_tv pm list packages -e | sed 's/^package://' | sort > "$dir/enabled-$stamp.txt"
    sh_tv pm list packages -d | sed 's/^package://' | sort > "$dir/disabled-before-$stamp.txt"
    [[ -f "$dir/original-home.txt" ]] || current_home > "$dir/original-home.txt"
    echo "Backup salvo em $dir"
}

# Procura pacotes ativos com nomes típicos de anúncio, telemetria e ballast de fabricante.
cmd_scan() {
    say "Pacotes ativos com nomes suspeitos (revise antes de desativar):"
    sh_tv pm list packages -e | sed 's/^package://' | sort |
        grep -Ei 'ads?([._]|$)|advert|analytic|telemetry|tracking|feedback|recommend|promo|survey|partner|marketing|report|metrics|bloat|ott\.|store|appmarket|smartdisplay|weishi|apphub|sonyselect|discovery' |
        grep -vE "$PROTECTED" || echo "(nada encontrado)"
    echo
    echo "Para desativar algum: $0 disable <pacote> [pacote...]"
}

disable_pkg() {
    local pkg="$1" log="$2"
    if [[ "$pkg" =~ $PROTECTED ]]; then
        echo "  ignorado:   $pkg (essencial para a TV funcionar)"
        return
    fi
    if sh_tv pm disable-user --user 0 "$pkg" | grep -q 'new state: disabled'; then
        grep -qx "$pkg" "$log" 2>/dev/null || echo "$pkg" >> "$log"
        echo "  desativado: $pkg"
    else
        echo "  falhou:     $pkg (a TV não permitiu)"
    fi
}

cmd_disable() {
    [[ $# -ge 1 ]] || die "uso: $0 disable <pacote> [pacote...]"
    cmd_backup >/dev/null
    local log; log="$(backup_dir)/disabled.txt"
    for pkg in "$@"; do disable_pkg "$pkg" "$log"; done
}

# Percorre uma ou mais listas de tools/packages/ e pergunta pacote a pacote.
cmd_debloat() {
    local lists=("$@")
    if [[ ${#lists[@]} -eq 0 ]]; then
        # Padrão: a lista comum mais a da marca da TV, se existir (ex.: sony.txt).
        lists=(common)
        local b; b=$(brand)
        [[ -f "$LISTS/$b.txt" ]] && lists+=("$b") && echo "TV $b detectada: usando também a lista \"$b\"."
    fi
    cmd_backup
    local log; log="$(backup_dir)/disabled.txt"
    for list in "${lists[@]}"; do
        local file="$LISTS/$list.txt"
        [[ -f "$file" ]] || die "lista não encontrada: $file"
        if [[ "$list" == launchers ]] && ! our_home_active; then
            die "o Launcher TV ainda não é a tela inicial. Rode \"$0 install\" antes de desativar os launchers."
        fi
        say "Lista: $list"
        while IFS= read -r line <&3; do
            local pkg="${line%%#*}"; pkg="${pkg//[[:space:]]/}"
            [[ -z "$pkg" ]] && continue
            local desc=""; [[ "$line" == *#* ]] && desc="${line#*#}"
            is_enabled "$pkg" || continue
            if ask "Desativar $pkg —${desc}?"; then disable_pkg "$pkg" "$log"; fi
        done 3< "$file"
    done
    echo "Pronto. Para desfazer tudo: $0 restore"
}

cmd_build() {
    say "Compilando o APK (precisa do Android SDK; veja o README)…"
    (cd "$ROOT" && ./gradlew --quiet assembleRelease)
}

cmd_install() {
    [[ -f "$APK" && "${1:-}" != "--rebuild" ]] || cmd_build
    cmd_backup >/dev/null
    say "Instalando $APK"
    adb -s "$SERIAL" install -r "$APK"
    if [[ "$(sdk)" -lt 24 ]]; then
        # Android 5/6 (ex.: Sony Bravia 2015-2016): não há comando para trocar a tela inicial.
        echo "Aperte Home no controle, escolha \"Launcher TV\" e depois \"Sempre\"."
        echo "Se a TV não perguntar: \"$0 debloat launchers\" desativa o launcher de fábrica."
        return
    fi
    say "Definindo o Launcher TV como tela inicial"
    sh_tv cmd package set-home-activity "$OUR_HOME" || true
    if [[ "$(current_home)" == "$OUR_PKG/"* ]]; then
        echo "Feito. Aperte Home no controle."
    else
        echo "A TV não aceitou a troca direta de tela inicial."
        echo "Opção: \"$0 debloat launchers\" desativa o launcher de fábrica (reversível com restore)."
    fi
}

cmd_restore() {
    local dir; dir=$(backup_dir)
    local log="$dir/disabled.txt"
    if [[ -s "$log" ]]; then
        say "Reativando $(wc -l < "$log") pacote(s)"
        while IFS= read -r pkg; do
            [[ -z "$pkg" ]] && continue
            sh_tv pm enable --user 0 "$pkg" >/dev/null && echo "  reativado: $pkg"
        done < "$log"
        mv "$log" "$log.restored-$(date +%Y%m%d-%H%M%S)"
    else
        echo "Nada desativado por este script nesta TV."
    fi
    if [[ -s "$dir/original-home.txt" ]]; then
        local home; home=$(cat "$dir/original-home.txt")
        if [[ "$home" == */* && "$home" != "$OUR_PKG/"* && "$(sdk)" -ge 24 ]]; then
            sh_tv cmd package set-home-activity "$home" >/dev/null || true
            echo "Tela inicial original: $home"
        fi
    fi
    if ask "Desinstalar também o Launcher TV?"; then
        adb -s "$SERIAL" uninstall "$OUR_PKG" || true
    fi
    echo "TV restaurada."
}

usage() {
    cat <<USAGE
Uso: $0 <comando>

  connect <ip>            Conecta à TV pela rede (porta 5555)
  info                    Marca, modelo, versão do Android e tela inicial atual
  backup                  Salva a lista de apps e a tela inicial atual
  install [--rebuild]     Instala o Launcher TV e define como tela inicial
  debloat [lista...]      Desativa apps das listas em tools/packages/ (padrão: common + a da marca),
                          perguntando um a um. Listas: $(ls "$LISTS" | sed 's/\.txt$//' | tr '\n' ' ')
  scan                    Mostra apps ativos com cara de anúncio/telemetria
  disable <pacote...>     Desativa pacotes específicos
  restore                 Reativa tudo o que foi desativado e volta a tela inicial original
  build                   Só compila o APK

Com mais de uma TV conectada, use TV=<ip:porta> $0 <comando>.
USAGE
}

main() {
    local cmd="${1:-}"; shift || true
    case "$cmd" in
        connect) need_adb; cmd_connect "$@" ;;
        build) cmd_build ;;
        info|backup|install|debloat|scan|disable|restore)
            need_adb; SERIAL=$(serial); "cmd_$cmd" "$@" ;;
        ""|-h|--help|help) usage ;;
        *) usage; exit 1 ;;
    esac
}

main "$@"

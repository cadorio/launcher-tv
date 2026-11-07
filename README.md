# Launcher TV

Uma tela inicial limpa para qualquer **Android TV / Google TV**, sem anúncios e sem fileiras de
“recomendações”, mais um script que desativa o lixo pré-instalado pela rede (Wi-Fi), via ADB.

- **Sem root, sem cabo, nada é desinstalado.** Os apps são desativados com `pm disable-user`
  e um único comando (`restore`) desfaz tudo.
- **Launcher nativo e leve** (Java puro, sem bibliotecas): relógio grande, fileira de favoritos
  e todos os apps em grade com banners 16:9. Feito para o controle remoto: o item em foco cresce
  e mostra o nome.
- **Funciona offline.** O launcher não pede internet nem coleta nada.

**TV Samsung (Tizen)?** Existe uma versão própria em [`tizen/`](tizen/README.md): o mesmo visual,
como um app que você abre (a Samsung não deixa trocar a tela inicial).

Inspirado no projeto [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT); o código
daqui foi escrito do zero.

## O que vem no launcher

| Recurso | Como usar |
|---|---|
| Favoritos | Segure **OK** (ou aperte **Menu**) num app → *Fixar nos favoritos*. Na fileira de favoritos, o mesmo menu move o app para a esquerda/direita. |
| Ocultar apps | Mesmo menu → *Ocultar app*. Para trazer de volta: ⚙ → *Mostrar apps ocultos*. |
| Relógio | ⚙ → mostrar/ocultar segundos. Segue o formato 12/24 h e o idioma da TV. |
| Fundo | ⚙ → alterna entre o gradiente próprio e o papel de parede da TV. |
| Voltar / Home | Na tela inicial, levam ao topo e ao primeiro favorito. |
| Primeira vez | Netflix, YouTube, Prime Video, Disney+, Max, Globoplay etc. que estiverem instalados viram os favoritos iniciais. |

Idiomas: português (padrão) e inglês.

## Passo a passo

### 1. Na TV

Configurações → Preferências do dispositivo → Sobre → aperte **OK sete vezes em “Build”** →
volte → Opções do desenvolvedor → ligue **Depuração USB** (e **Depuração de rede/ADB**, se existir).
Anote o IP da TV (Configurações → Rede).

### 2. No computador (mesma rede Wi-Fi)

Precisa do [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
(`adb`) e, para compilar, do JDK 17 + Android SDK (o Android Studio instala os dois).

```bash
git clone https://github.com/cadorio/launcher-tv && cd launcher-tv

./tools/tv.sh connect 192.168.0.50   # aceite "Permitir depuração" na TV
./tools/tv.sh install                # compila, instala e define como tela inicial
./tools/tv.sh debloat                # desativa anúncios/telemetria, perguntando um a um
```

Sem SDK para compilar? Baixe o APK pronto em **Actions → build → launcher-tv-apk** e coloque em
`app/build/outputs/apk/release/app-release.apk` antes do `install`.

### 3. Se a TV insistir no launcher de fábrica

Algumas TVs (principalmente Google TV) ignoram a troca de tela inicial. Depois do `install`:

```bash
./tools/tv.sh debloat launchers      # desativa o launcher original (reversível)
```

## Todos os comandos

| Comando | O que faz |
|---|---|
| `connect <ip>` | Conecta à TV pela rede (porta 5555). |
| `backup` | Salva a lista de apps e a tela inicial atual em `backup/<tv>/`. |
| `install [--rebuild]` | Instala o launcher e define como tela inicial. |
| `debloat [lista...]` | Desativa os apps das listas em [`tools/packages/`](tools/packages) — `common` (padrão), `launchers`, `optional` —, sempre perguntando. |
| `scan` | Mostra apps ativos com nomes típicos de anúncio/telemetria do fabricante. |
| `disable <pacote...>` | Desativa pacotes específicos (ex.: algo achado pelo `scan`). |
| `restore` | Reativa tudo o que o script desativou, volta a tela inicial original e (se quiser) desinstala o launcher. |

Com mais de uma TV conectada: `TV=192.168.0.50:5555 ./tools/tv.sh <comando>`.

Cada fabricante (TCL, Philips, Sony, Xiaomi, Hisense…) traz um lixo diferente. Rode `scan`,
confira os nomes e desative só o que você reconhece. Para ensinar uma marca nova ao script,
crie `tools/packages/<marca>.txt` no mesmo formato (`pacote  # descrição`) e rode
`./tools/tv.sh debloat <marca>`.

## Desfazer

```bash
./tools/tv.sh restore
```

Também dá para desfazer sem o computador: Configurações → Apps → *app desativado* → Ativar.
Uma restauração de fábrica também reativa tudo.

## Estrutura

```
app/                      launcher Android (Java, minSdk 26, sem dependências)
  src/main/java/.../HomeActivity.java   tela inicial e navegação por controle
  src/main/java/.../AppStore.java       lista de apps, favoritos e ocultos
tizen/                    versão para TVs Samsung (web app Tizen) e build.sh para instalar
tools/tv.sh               conectar, instalar, limpar e restaurar via ADB
tools/packages/*.txt      listas de pacotes para desativar
```

## Licença

MIT.

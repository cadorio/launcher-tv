# Launcher TV para Samsung (Tizen)

Versão do Launcher TV para TVs Samsung com Tizen OS (2017 em diante): relógio, fileira de
favoritos e todos os apps da TV numa grade, navegável pelo controle.

> **Diferença importante para o Android:** a Samsung não permite trocar a tela inicial (Smart Hub)
> nem desativar apps de fábrica. Aqui o Launcher TV é **um app que você abre** pela barra de apps.
> Dica: fixe-o na primeira posição da barra (Apps → segure OK no Launcher TV → Mover).

## Controle

| Tecla | Ação |
|---|---|
| Setas + **OK** | Navegar e abrir app |
| **Segurar OK** ou botão **vermelho** | Opções do app: fixar/remover dos favoritos, mover, ocultar |
| Botão **verde** ou ⚙ | Ajustes: apps ocultos, segundos no relógio, organização padrão, sair |
| **Voltar** | Volta ao topo; no topo, sai do app |

Favoritos e apps ocultos ficam salvos na própria TV.

## Instalar

### 1. Computador

1. Instale o [Tizen Studio](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html) (a versão com CLI basta).
2. No **Package Manager** do Tizen Studio → aba *Extension SDK* → instale **TV Extensions** e
   **Samsung Certificate Extension**.

### 2. TV em Modo Desenvolvedor

1. Na TV, abra **Apps** e digite **1 2 3 4 5** no controle (no controle sem números, use o
   teclado numérico virtual).
2. Ligue **Developer mode** e coloque o **IP do seu computador** em *Host PC IP*.
3. Desligue e ligue a TV (tirar da tomada por alguns segundos garante).

### 3. Certificado Samsung (uma vez só)

A Samsung só instala apps assinados com um certificado ligado à sua TV:

1. Conecte: `~/tizen-studio/tools/sdb connect <ip-da-tv>`
2. Tizen Studio → **Tools → Certificate Manager** → **+** → *Samsung* → *TV* → nome do perfil
   **`launchertv`** → entre com sua conta Samsung. Quando pedir os aparelhos (DUID), escolha
   a TV conectada.

### 4. Instalar

```bash
./tizen/build.sh install <ip-da-tv>
```

O script empacota (`build/LauncherTV.wgt`), assina com o perfil `launchertv`, instala e abre o
app. Para outro nome de perfil: `TIZEN_PROFILE=meuperfil ./tizen/build.sh install <ip>`.

## Testar sem TV

Abra `tizen/index.html` no Chrome com a janela em 1920×1080. Sem a API do Tizen, ele mostra
apps de exemplo. Setas + Enter navegam; **M** = opções do app, **S** = ajustes, **Esc** = voltar.

## Limitações conhecidas

- Não substitui a tela inicial da Samsung, nem desativa apps ou anúncios do sistema.
- Apps instalados em Modo Desenvolvedor podem sumir depois de uma atualização de firmware;
  basta rodar o `install` de novo.
- O Tizen não fornece banners dos apps, só ícones; os blocos mostram ícone + nome.
- Ainda não foi testado numa TV real. Se a lista de apps vier vazia, o modelo restringiu a
  permissão `application.info`. Abra uma issue com o modelo e o ano da TV.

## Menos rastreamento na Samsung (pelo controle)

Configurações → Geral → **Termos e Privacidade** (o caminho varia por ano):

- desligue **Serviços de Informações de Visualização** (o rastreamento do que passa na tela);
- desligue **Anúncios baseados em interesses** e **Personalizar anúncios**;
- em *Privacidade*, desligue **Serviço de reconhecimento de voz** se não usa o Bixby/Alexa.

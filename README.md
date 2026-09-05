# 📍 Marcador WME (Locador WME)

Ferramenta de apoio para levantamento de coordenadas em campo e posterior análise no **Waze Map Editor (WME)**.

O projeto é composto por **duas partes que trabalham juntas**:

1. **App Android "Marcador"** — usado em campo para capturar coordenadas GPS/GNSS, organizá-las por categoria e exportá-las como KML/JSON.
2. **Userscript "Locador WME"** (Tampermonkey/Violentmonkey) — usado depois, no navegador, para carregar o KML exportado e navegar ponto a ponto dentro do próprio Waze Map Editor, marcando o que já foi analisado.

---

## 🎯 Objetivo

Ao mapear irregularidades em campo (lombadas, radares, postos, pontos de perigo etc.), o app permite registrar rapidamente a localização com um toque, sem depender de conexão com internet. Esses pontos podem depois ser exportados como KML e carregados no WME através do userscript, que oferece uma navegação guiada (ponto a ponto) para conferência e edição no mapa, com controle de quais pontos já foram revisados.

---

## 🧩 Componentes do repositório

| Componente              | Localização                     | Tecnologia                                     |
| ------------------------ | -------------------------------- | ------------------------------------------------ |
| App Android               | `app/`                          | Kotlin, Android SDK (`MainActivity.kt`)          |
| Userscript para o WME      | `Locador_WME_2.4.0.user.js`     | JavaScript puro, sobre o **SDK oficial do WME**  |

---

## 📱 Parte 1 — App Android "Marcador"

Todo o app é implementado em uma única `Activity` (`MainActivity.kt`, ~2400 linhas), sem telas/Activities adicionais — as telas de "Configurações" e "Monitor GNSS" são `Dialog`s internos, conforme comentários no próprio código ("continua dentro do MainActivity, sem nova Activity").

### Tela principal
- Mantém a tela do aparelho sempre ligada (`FLAG_KEEP_SCREEN_ON`), pensado para uso durante a condução.
- Exibe em tempo real: latitude/longitude (7 casas decimais), precisão do GPS, quantidade de satélites e status da leitura.
- Mostra os botões de categoria em uma grade de 2 colunas (`GridLayoutManager`); cada botão usa a cor cadastrada da categoria, com a cor do texto calculada automaticamente (preto ou branco) a partir da luminância do fundo, para manter a legibilidade.
- Contador de "Total" de pontos salvos.

### Captura de localização (GPS)
- Usa `LocationManager` com os provedores `GPS_PROVIDER` (atualização a cada 500 ms) e, se disponível, `NETWORK_PROVIDER` (a cada 1000 ms) como apoio.
- Ao abrir, tenta usar a última localização conhecida do GPS se ela tiver no máximo 30 segundos.
- Lógica de prioridade entre provedores: uma leitura vinda do `NETWORK_PROVIDER` é **descartada** se a última leitura válida veio do GPS e tem no máximo 10 segundos — ou seja, o GPS tem prioridade sobre a triangulação por rede quando ainda está "fresco".
- Validações aplicadas antes de aceitar uma leitura: latitude/longitude finitas e dentro do intervalo válido (-90..90 / -180..180).

### Validações ao salvar um ponto
Antes de gravar um ponto, o app verifica, nesta ordem:
1. Se existe uma localização atual disponível (senão, reinicia a captura de GPS e avisa o usuário).
2. Se a coordenada é numericamente válida.
3. Se a precisão (`accuracy`) é um valor finito e maior que zero.
4. Se a precisão é **igual ou melhor que 100 metros** — leituras piores que isso são recusadas com a mensagem `"Precisão GPS insuficiente: X m"`.

Cada ponto salvo grava:
- Nome da categoria
- Latitude / longitude
- Data e hora (`yyyy-MM-dd HH:mm:ss`)
- Precisão (m)
- Provedor da localização (`gps`, `network`, etc.)
- Quantidade de satélites usados no cálculo (prioriza o valor real vindo do `GnssStatus`; só usa o campo "extras" do `Location` como reserva, e nunca inventa um número)

### Categorias personalizáveis
Categorias padrão criadas na primeira execução (só são recriadas se a lista salva estiver vazia):

| Categoria | Cor padrão |
|---|---|
| 🚧 Quebra-mola | laranja escuro |
| ⛽ Posto | verde |
| 📡 Radar | vermelho |
| ⚠️ Perigo | amarelo/dourado |

Na tela de Configurações também é possível:
- Criar novas categorias, com um ID gerado automaticamente a partir do nome (normalizado, sem acentos)
- Excluir categorias existentes
- Trocar a cor de qualquer categoria em uma paleta fixa de 12 cores predefinidas (vermelho, laranja, amarelo, verde, ciano, azul, roxo, rosa, marrom, cinza, verde-azulado, terracota)

Categorias e pontos são persistidos localmente em `SharedPreferences`, serializados como JSON — não há backend, conta de usuário ou sincronização em nuvem.

### Monitor GNSS
Tela dedicada (acessível pelas Configurações) que lê diretamente a API `GnssStatus` do Android e exibe, por satélite:
- Constelação (GPS, SBAS, GLONASS, QZSS, BEIDOU, GALILEO, NAVIC/IRNSS, ou "DESCONHECIDA")
- Identificador do satélite (PRN para GPS, ID para as demais constelações)
- Azimute e elevação
- C/N₀ (relação sinal-ruído), com codificação de cor: **verde** (≥ 35 dB-Hz, sinal forte), **laranja** (≥ 20 dB-Hz, sinal médio) e **cinza** (abaixo disso, sinal fraco)
- Se está sendo efetivamente usado no cálculo da posição (`usedInFix`)
- Presença de dados de efemérides e almanaque
- Frequência da portadora (quando o aparelho e a versão do Android expõem esse dado — API 26+) e C/N₀ de banda base (API 30+)

Todos os valores vêm diretamente do sistema operacional; o código deixa explícito em comentário que "nenhum campo é inventado ou simulado" — campos não suportados pelo aparelho aparecem como nulos em vez de um valor fictício.

### Sky View
Dentro do Monitor GNSS, uma visualização gráfica (`View` customizada com `Canvas`) posiciona cada satélite no céu usando seu azimute/elevação reais, com indicação de Norte/Sul/Leste/Oeste, diferenciação visual entre satélites usados e apenas visíveis, e é possível tocar em um satélite para ver seus detalhes.

### Permalink do Waze
Cada ponto tem um link direto para abrir sua localização no WME, no formato:

```
https://www.waze.com/pt-BR/editor?env=row&lat=...&lon=...&marker=true&zoomLevel=20
```

Na lista de pontos salvos (tela de Configurações), um toque longo compartilha esse link isoladamente pelo sistema de compartilhamento do Android.

### Exportação de dados
Na tela de Configurações existem os botões **"Exportar JSON"** e **"Exportar KML"**. O código já define um `enum` interno (`FormatoExportacao`) com as opções `JSON`, `KML`, `CSV`, `GPX` e `GEOJSON`, mas **apenas JSON e KML estão implementados** — selecionar CSV, GPX ou GeoJSON hoje mostra o aviso "Este formato de exportação ainda não foi implementado.", ou seja, são formatos previstos na arquitetura mas ainda não expostos na interface.

**JSON exportado** (formato real gerado pelo app):
```json
{
  "status": "success",
  "total": 2,
  "pontos": [
    {
      "nome": "Radar",
      "latitude": -9.50982,
      "longitude": -35.82036,
      "coordenadas": "-9.50982, -35.82036",
      "permalink": "https://www.waze.com/pt-BR/editor?env=row&lat=-9.50982&lon=-35.82036&marker=true&zoomLevel=20",
      "data": "2025-01-01 10:00:00",
      "precisao": 8.5,
      "provedor": "gps",
      "satelites": 12
    }
  ]
}
```

**KML exportado**: cada ponto vira um `<Placemark>` com `<name>`, uma `<description>` em `CDATA` contendo coordenadas formatadas, data, precisão, satélites, provedor e o permalink do Waze, e um `<Point>` com as coordenadas no padrão KML (`longitude,latitude,altitude`).

O arquivo (JSON ou KML) é escrito no cache do app e compartilhado via `FileProvider`/`Intent` de compartilhamento do Android — pode ser enviado por e-mail, Drive, WhatsApp, ou salvo direto no aparelho para depois ser aberto pelo userscript.

### Permissões usadas
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

Não há coleta de dados por terceiros nem envio a servidores: tudo fica no aparelho até o usuário exportar/compartilhar manualmente.

---

## 🌐 Parte 2 — Userscript "Locador WME"

Arquivo: [`Locador_WME_2.4.0.user.js`](./Locador_WME_2.4.0.user.js) (v2.4.0). Roda automaticamente ao abrir `https://www.waze.com/*/editor*` (declarado no cabeçalho `@match` do próprio userscript), e usa o **SDK oficial do WME** (`window.getWmeSdk`) em vez de manipular o DOM do mapa diretamente — o script aguarda `window.SDK_INITIALIZED` antes de se inicializar, com nova tentativa a cada 500 ms enquanto o SDK ainda não está pronto.

> O arquivo fica propositalmente na raiz do repositório (fora de qualquer subpasta), pois é esse caminho que permite a instalação direta pelo Tampermonkey a partir de um link "raw" do GitHub — ver instruções abaixo.

### Painel flutuante
Ao iniciar, o script injeta um painel (`#locador-wme-painel`) sobre o WME, com:
- Cabeçalho arrastável (arraste pelo ícone `⠿`) e botão de minimizar
- Alça de redimensionamento vertical (arraste o `↕` para ajustar a altura, com altura mínima de 180px)
- Posição e altura do painel são salvas em `localStorage` (`locador_wme_painel_posicao_v1` e `locador_wme_painel_altura_v2`) e restauradas na próxima visita
- Campo **"CARREGAR KML"** (`input type="file"`, aceita `.kml`/`.xml`)
- Contadores de **TOTAL**, **ANALISADOS** e **PENDENTES**
- Lista **"PRÓXIMOS PONTOS"**: mostra até 10 pontos por vez, centrada no ponto atual, cada item clicável para navegar direto até ele — com indicador visual de estado (⭐ atual, ✓ analisado, ○ pendente)
- Botões de navegação **◀ / 📍 IR AO PONTO / ▶** e atalhos de teclado `←`/`→` para ponto anterior/próximo (desativados automaticamente se o foco estiver em um campo de texto)
- Botão **"☐ MARCAR COMO ANALISADO"**
- Botão **"🗑 LIMPAR KML"**

### Leitura do KML
O KML é lido no navegador com `DOMParser`, sem depender de bibliotecas externas. Para cada `<Placemark>`, o script extrai:
- O primeiro par de coordenadas de `<coordinates>` (longitude, latitude e, opcionalmente, altitude)
- `<name>` (usa "Sem nome" se vazio) e `<description>`

Se o arquivo não tiver nenhuma coordenada válida, o carregamento é interrompido com uma mensagem de erro. Ao carregar com sucesso, o script navega automaticamente para o primeiro ponto.

### Marcadores no mapa
Os pontos são desenhados em uma camada própria (`sdk.Map.addLayer`, camada `locador-wme-pontos`) usando regras de estilo (`styleRules`) baseadas no SDK do WME, com marcadores SVG gerados dinamicamente:
- 🔴 **Círculo vermelho** — ponto pendente (padrão)
- 🟢 **Círculo verde** — ponto marcado como analisado
- ⭐ **Estrela laranja** (maior que os círculos) — ponto atualmente selecionado

### Navegação e progresso
- **Ir ao ponto**: usa `sdk.Map.setMapCenter({ lonLat: { lon, lat }, zoomLevel: 20 })`, a API oficial do WME, para centralizar o mapa exatamente na coordenada do ponto.
- **Marcar como analisado**: alterna o estado do ponto atual e, se ele passou a "analisado", avança automaticamente para o próximo ponto (e já centraliza o mapa nele) — pensado para revisar a lista inteira sem precisar tocar em "próximo" a cada item.
- O progresso (quais pontos já foram analisados) é salvo em `localStorage` sob a chave `locador_wme_progresso_v2`, e é restaurado automaticamente ao recarregar um KML com o mesmo conteúdo — o trabalho de revisão sobrevive a reinícios do navegador.

### Como instalar o userscript
1. Instale a extensão [Tampermonkey](https://www.tampermonkey.net/) (ou outra compatível, como Violentmonkey) no navegador.
2. Abra o link "raw" do script diretamente no navegador — o Tampermonkey deve reconhecer o arquivo `.user.js` e oferecer a instalação automaticamente:
   [`https://raw.githubusercontent.com/clenildo96/App_Marcador_waze/main/Locador_WME_2.4.0.user.js`](https://raw.githubusercontent.com/clenildo96/App_Marcador_waze/main/Locador_WME_2.4.0.user.js)
3. Confirme a instalação na janela que o Tampermonkey abrir.
4. Abra o Waze Map Editor — o painel "Locador WME" deve aparecer automaticamente no canto da tela.
5. Clique em "📂 CARREGAR KML" e selecione o arquivo exportado pelo app Android (ou qualquer outro KML com `Placemark`s de coordenadas).
6. Use ◀ / ▶ (ou as setas do teclado) para navegar, e "☐ MARCAR COMO ANALISADO" para registrar a revisão de cada ponto.

---

## 🛠️ Estrutura do projeto

```
App_Marcador_waze/
├── Locador_WME_2.4.0.user.js     # Userscript para o Waze Map Editor (raiz por design)
├── estrutura_projeto.txt
├── build.gradle.kts              # Build script raiz
├── settings.gradle.kts           # rootProject.name = "suporte_waze"; include(":app")
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   └── libs.versions.toml        # Catálogo de versões (AGP, Kotlin, AndroidX...)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/marcador/
        │   └── MainActivity.kt    # Toda a lógica do app: Activity, telas de Configurações
        │                          # e Monitor GNSS (como Dialogs internos), adapter da
        │                          # grade de categorias, exportação, Sky View e data classes
        │                          # Categoria / Ponto / SatelliteInfo (~2400 linhas)
        └── res/
            ├── drawable/          # Ícones das categorias padrão (ic_quebra_mola, ic_posto, ic_radar, ic_perigo)
            ├── layout/            # activity_main.xml, dialog_nova_categoria.xml
            ├── mipmap-*/          # Ícone do app
            ├── values/ , values-night/
            └── xml/               # file_paths.xml (FileProvider), backup rules
```

A lógica inteira do app (localização, categorias, persistência, exportação, compartilhamento, monitor GNSS e Sky View) está concentrada em `MainActivity.kt` — não há separação em outras classes/arquivos além dos data classes `Categoria`, `Ponto` e `SatelliteInfo` definidos no mesmo arquivo, e do `CategoriaAdapter` (RecyclerView) usado para a grade de botões.

---

## 📋 Requisitos para desenvolvimento

- Android Studio (compatível com a versão do AGP declarada em `gradle/libs.versions.toml`)
- Android SDK com a `compileSdk`/`targetSdk` declarada em `app/build.gradle.kts`
- JDK compatível com a configuração do projeto
- Um dispositivo Android físico com GPS/GNSS para testar captura de localização e o monitor GNSS (o emulador não fornece dados reais de satélites)
- Uma extensão de userscripts no navegador (Tampermonkey ou similar) para usar o `Locador_WME_2.4.0.user.js`

### Compilando o app
```bash
git clone https://github.com/clenildo96/App_Marcador_waze.git
cd App_Marcador_waze
./gradlew assembleDebug
```

Para gerar um build de `release` assinado, é necessário configurar o keystore e as variáveis de ambiente referenciadas em `app/build.gradle.kts`.

---

## 🔐 Privacidade e dados

- O app usa a localização do dispositivo apenas para registrar as coordenadas dos pontos.
- Categorias e pontos ficam salvos localmente (`SharedPreferences`); nada é enviado a servidores próprios.
- O userscript guarda o progresso de navegação/análise localmente no navegador (`localStorage`), por domínio.
- Exportação e compartilhamento (JSON, KML, permalink) são ações explícitas do usuário.

---

## 📌 Status do projeto

**Em desenvolvimento.** As funcionalidades principais de captura, categorização, exportação (JSON/KML) e navegação assistida no WME estão implementadas; formatos de exportação adicionais (CSV, GPX, GeoJSON) já estão previstos no código, mas ainda não expostos na interface.

---

## 👨‍💻 Autor

Desenvolvido por **Clenildo Teixeira**, como ferramenta independente de apoio ao trabalho de levantamento e organização de coordenadas em campo para uso com o Waze Map Editor.

---

## ⚠️ Observação legal

O Marcador WME / Locador WME é uma ferramenta independente e não é um aplicativo ou script oficial do Waze. Waze e Waze Map Editor são marcas de seus respectivos proprietários.

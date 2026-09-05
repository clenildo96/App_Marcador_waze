# 📍 Marcador WME

Aplicativo Android desenvolvido para auxiliar levantamentos de coordenadas em campo, permitindo registrar, organizar, visualizar e exportar pontos geográficos para posterior utilização no trabalho com o **Waze Map Editor (WME)**.

O aplicativo utiliza os recursos de localização e GNSS disponíveis no próprio aparelho Android para obter as coordenadas e informações dos satélites.

---

## 🎯 Objetivo

O Marcador WME foi desenvolvido para facilitar o registro de pontos durante levantamentos em campo.

Cada ponto registrado fica associado à categoria selecionada e armazena informações como:

- Latitude
- Longitude
- Data e hora do registro
- Precisão da localização
- Provedor da localização
- Quantidade de satélites utilizados

Os pontos podem posteriormente ser compartilhados ou exportados para outros formatos.

---

## 🚀 Principais recursos

### 📍 Registro de pontos

O aplicativo obtém a localização atual do dispositivo e permite salvar um ponto através de uma categoria.

Cada ponto armazenado contém:

- Nome da categoria
- Latitude
- Longitude
- Precisão GPS
- Data e hora
- Provedor da localização
- Número de satélites utilizados no cálculo

O aplicativo também verifica a validade das coordenadas e não permite salvar pontos quando a precisão da localização é considerada insuficiente.

---

### 🗂️ Categorias personalizáveis

O aplicativo possui categorias para organizar os pontos registrados.

Categorias padrão:

- 🚧 Quebra-mola
- ⛽ Posto
- 📡 Radar
- ⚠️ Perigo

Também é possível:

- Criar novas categorias
- Excluir categorias
- Alterar a cor de cada categoria
- Utilizar uma paleta de cores para facilitar a identificação visual

As categorias são armazenadas localmente no dispositivo.

---

## 🛰️ Monitor GNSS

O aplicativo possui um **Monitor GNSS** que utiliza a API `GnssStatus` do Android para apresentar informações reais recebidas pelo aparelho.

O monitor apresenta:

- Quantidade de satélites visíveis
- Quantidade de satélites utilizados no cálculo
- Constelações GNSS detectadas
- Identificação dos satélites
- Elevação
- Azimute
- C/N₀
- Status de utilização no cálculo
- Dados de efemérides
- Dados de almanaque
- Frequência da portadora, quando disponível
- C/N₀ de baseband, quando disponível

Os dados apresentados são provenientes do sistema GNSS do próprio dispositivo. O aplicativo não gera ou simula informações de satélites.

---

## 🌌 Sky View

O Monitor GNSS possui uma visualização **Sky View**, representando graficamente a posição dos satélites no céu.

A representação utiliza:

- Azimute real do satélite
- Elevação real do satélite
- Identificação das direções Norte, Sul, Leste e Oeste
- Diferenciação entre satélites utilizados e apenas visíveis
- Indicador visual da intensidade do sinal

Também é possível tocar sobre um satélite para visualizar seus detalhes.

---

## 🔗 Permalink do Waze

Cada ponto registrado possui um permalink gerado a partir de suas coordenadas.

O aplicativo utiliza o formato:

`https://www.waze.com/pt-BR/editor?env=row&lat=...&lon=...&marker=true&zoomLevel=20`

Na tela de pontos salvos, é possível manter pressionado um ponto para compartilhar seu link do Waze através do sistema de compartilhamento do Android.

---

## 📤 Exportação

Os pontos podem ser exportados através da tela de configurações.

### JSON

O arquivo JSON contém os pontos registrados e suas informações, incluindo:

- Nome
- Latitude
- Longitude
- Coordenadas
- Permalink do Waze
- Data
- Precisão
- Provedor
- Satélites

### KML

O aplicativo também gera arquivos KML contendo os pontos como `Placemark`, incluindo suas coordenadas e informações associadas.

O KML pode ser utilizado em aplicações compatíveis com esse formato de dados geográficos.

---

## 💾 Armazenamento

Os dados do aplicativo são armazenados localmente no dispositivo utilizando `SharedPreferences`.

São persistidos:

- Categorias
- Pontos registrados
- Cores das categorias

Não há, na implementação atual, necessidade de um servidor externo para armazenar esses dados.

---

## 📱 Requisitos

O projeto é um aplicativo Android desenvolvido em Kotlin utilizando Gradle.

Para desenvolvimento e compilação são necessários:

- Android Studio
- Android SDK
- JDK compatível com a configuração do projeto
- Dispositivo Android com recursos de localização/GNSS para utilização dos recursos de GPS e monitoramento de satélites

---

## 🛠️ Estrutura do projeto

```text
MarcadorCoordenadas/
│
├── aplicativo/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/marcador/
│           │       └── MainActivity.kt
│           │
│           ├── res/
│           │   ├── drawable/
│           │   │   ├── ic_perigo.xml
│           │   │   ├── ic_posto.xml
│           │   │   ├── ic_quebra_mola.xml
│           │   │   └── ic_radar.xml
│           │   │
│           │   ├── layout/
│           │   │   ├── activity_main.xml
│           │   │   └── dialog_nova_categoria.xml
│           │   │
│           │   ├── mipmap-*/
│           │   ├── values/
│           │   ├── values-night/
│           │   └── xml/
│           │
│           └── AndroidManifest.xml
│
├── gradle/
│   └── wrapper/
│
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── configurações.gradle.kts
└── README.md
```

A implementação principal encontra-se atualmente concentrada na `MainActivity.kt`, que contém a lógica de localização, armazenamento de pontos, categorias, exportação, compartilhamento, monitor GNSS e visualização Sky View.

---

## 🔐 Privacidade e dados

O aplicativo utiliza a localização do dispositivo para obter as coordenadas dos pontos registrados.

Os dados dos pontos são armazenados localmente no aparelho.

O aplicativo não necessita de uma conta ou de um servidor próprio para registrar os pontos.

---

## 📌 Status do projeto

**Em desenvolvimento.**

O projeto está sendo desenvolvido como uma ferramenta de apoio para levantamentos de coordenadas e atividades relacionadas ao Waze Map Editor.

---

## 👨‍💻 Autor

Desenvolvido por **Clenildo Teixeira**.

Projeto independente desenvolvido para facilitar o trabalho de levantamento e organização de coordenadas em campo.

---

## ⚠️ Observação

O Marcador WME é uma ferramenta independente e não é um aplicativo oficial do Waze.

Waze e Waze Map Editor são marcas de seus respectivos proprietários.

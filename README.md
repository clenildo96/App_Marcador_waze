# 📍 Marcador WME + Locador WME

Ferramenta independente desenvolvida para auxiliar o levantamento de coordenadas em campo e a posterior análise e edição no **Waze Map Editor (WME)**.

O projeto reúne um aplicativo Android, chamado **Marcador**, e um userscript para o WME, chamado **Locador WME**.

A proposta é simples:

**capturar pontos em campo → organizar por categoria → exportar → revisar os pontos no WME.**

---

## 🎯 Objetivo

Durante um levantamento em campo, o usuário pode registrar rapidamente a localização de pontos de interesse, como:

* 🚧 Quebra-molas
* ⛽ Postos
* 📡 Radares
* ⚠️ Situações de perigo
* outras categorias criadas pelo próprio usuário

As coordenadas são armazenadas localmente no aparelho e posteriormente podem ser exportadas em **JSON ou KML**.

O KML pode então ser carregado no **Locador WME**, permitindo navegar pelos pontos diretamente no Waze Map Editor e controlar quais já foram analisados.

O projeto foi desenvolvido como uma ferramenta independente de apoio aos editores.

---

# 🧩 Componentes

O projeto possui dois componentes principais:

| Componente     | Localização                 | Tecnologia           |
| -------------- | --------------------------- | -------------------- |
| 📱 Marcador    | `app/`                      | Kotlin / Android     |
| 🌐 Locador WME | `Locador_WME_2.4.0.user.js` | JavaScript / WME SDK |

---

# 📱 Aplicativo Android — Marcador

O aplicativo é utilizado durante o levantamento em campo para capturar e organizar coordenadas.

## 🗺️ Tela principal

A tela principal apresenta:

* latitude e longitude atuais;
* precisão da localização;
* quantidade de satélites;
* status da localização;
* categorias configuradas;
* quantidade de pontos salvos;
* acesso às configurações;
* compartilhamento dos dados.

A localização é obtida diretamente pelos recursos de localização/GNSS disponíveis no dispositivo.

---

# 📍 Categorias personalizáveis

O aplicativo possui categorias padrão, mas permite que o usuário crie suas próprias categorias.

Categorias padrão:

* 🚧 Quebra-mola
* ⛽ Posto
* 📡 Radar
* ⚠️ Perigo

É possível:

* criar categorias;
* excluir categorias;
* alterar a cor;
* utilizar diferentes categorias para diferentes tipos de levantamento.

As categorias são armazenadas localmente no aparelho.

---

# 📡 Monitor GNSS

O aplicativo possui um **Monitor GNSS** para visualizar informações dos satélites disponíveis.

Quando fornecidos pelo dispositivo e pelo sistema operacional, podem ser apresentados dados como:

* constelação;
* identificação do satélite;
* azimute;
* elevação;
* C/N₀;
* indicação de utilização no cálculo da posição;
* efemérides;
* almanaque;
* frequência da portadora;
* informações de banda base.

Os dados apresentados são provenientes das APIs GNSS do Android.

Quando determinado dado não é fornecido pelo dispositivo, ele não é artificialmente preenchido.

---

# 🌌 Sky View

O Monitor GNSS também possui uma visualização gráfica dos satélites.

Os satélites são posicionados de acordo com seus valores reais de:

* azimute;
* elevação.

A visualização permite identificar a distribuição dos satélites no céu e consultar informações individuais.

---

# 🚗 Android Auto

O aplicativo possui integração com **Android Auto** utilizando a biblioteca AndroidX Car App.

A interface automotiva permite utilizar o aplicativo durante o levantamento sem precisar manter o celular na mão.

O Android Auto utiliza os mesmos dados armazenados pelo aplicativo principal.

Isso significa que os pontos registrados pelo Android Auto ficam disponíveis posteriormente no aplicativo Android convencional.

### Arquitetura dos dados

O aplicativo utiliza armazenamento local compartilhado por meio de `SharedPreferences`.

Assim:

```text
              ┌─────────────────────┐
              │  SharedPreferences  │
              │  marcador_preferencias
              └──────────┬──────────┘
                         │
             ┌───────────┴───────────┐
             │                       │
       Aplicativo Android       Android Auto
             │                       │
             └───────────┬───────────┘
                         │
                   Pontos salvos
```

Não existe um servidor intermediário para sincronizar esses dados.

---

# 🪟 Modo Flutuante

Uma das funcionalidades mais recentes do projeto é o **Modo Flutuante**.

Ele permite utilizar uma pequena interface sobre outros aplicativos, como o Waze, sem precisar abandonar a navegação.

O modo pode ser ativado pela tela de **Configurações** do aplicativo.

Depois de ativado, o serviço funciona independentemente da tela principal do Marcador.

## Recursos do modo flutuante

A interface foi projetada para permitir:

* exibição sobre outros aplicativos;
* uso durante a navegação;
* acesso rápido às categorias;
* registro de pontos sem retornar à tela principal;
* painel recolhível;
* botão flutuante;
* movimentação da interface;
* fechamento do painel;
* utilização das categorias cadastradas no aplicativo.

A funcionalidade utiliza um **Foreground Service** e uma janela do tipo `TYPE_APPLICATION_OVERLAY`.

Para funcionar, o Android exige que o usuário conceda a permissão de **exibição sobre outros aplicativos**.

### Fluxo

```text
Marcador
   │
   ├── Configurações
   │
   └── Modo Flutuante
            │
            ▼
    Foreground Service
            │
            ▼
      Painel flutuante
            │
            ▼
          Waze
            │
            ▼
      Registro do ponto
            │
            ▼
    Mesmo armazenamento
       do aplicativo
```

O modo flutuante não depende do Waze para armazenar os dados.

---

# 📍 Registro dos pontos

Antes de salvar um ponto, o aplicativo verifica a disponibilidade e a validade da localização.

Cada ponto armazenado contém informações como:

* nome da categoria;
* latitude;
* longitude;
* coordenadas;
* data e hora;
* precisão;
* provedor da localização;
* quantidade de satélites;
* permalink para o Waze Map Editor.

Exemplo simplificado:

```json
{
  "nome": "Radar",
  "latitude": -9.50982,
  "longitude": -35.82036,
  "coordenadas": "-9.50982, -35.82036",
  "data": "2026-09-05 18:30:00",
  "precisao": 8.5,
  "provedor": "gps",
  "satelites": 12
}
```

---

# 🔗 Permalink do Waze

Cada ponto pode possuir um permalink direcionando diretamente para sua localização no Waze Map Editor.

O endereço contém as coordenadas do ponto e parâmetros para posicionar o mapa.

Isso permite utilizar o ponto posteriormente durante a edição no WME.

---

# 📤 Exportação

O aplicativo atualmente disponibiliza:

* **JSON**
* **KML**

Os arquivos podem ser compartilhados utilizando o sistema de compartilhamento do Android.

O KML pode ser utilizado diretamente pelo **Locador WME**.

Outros formatos podem ser adicionados futuramente.

---

# 🌐 Locador WME

O **Locador WME** é um userscript executado no navegador enquanto o usuário trabalha no Waze Map Editor.

Arquivo:

```text
Locador_WME_2.4.0.user.js
```

O script utiliza o SDK oficial disponibilizado pelo Waze Map Editor para interagir com o mapa.

---

# 🧭 Painel do Locador WME

O Locador WME disponibiliza um painel sobre o WME para auxiliar na revisão dos pontos.

Entre seus recursos estão:

* carregar arquivos KML;
* visualizar o total de pontos;
* visualizar pontos analisados;
* visualizar pontos pendentes;
* navegar para o ponto anterior;
* navegar para o próximo ponto;
* selecionar diretamente um ponto da lista;
* ir diretamente ao ponto atual;
* marcar ponto como analisado;
* limpar o KML carregado;
* redimensionar o painel;
* movimentar o painel;
* minimizar o painel.

A posição e outras configurações da interface são armazenadas localmente no navegador.

---

# 📌 Navegação ponto a ponto

Depois que um KML é carregado, os pontos podem ser analisados individualmente.

O Locador permite:

```text
◀ Anterior
   │
   ▼
📍 Ponto atual
   │
   ▼
▶ Próximo
```

Ao marcar um ponto como analisado, o sistema pode avançar automaticamente para o próximo ponto.

Isso facilita a revisão de grandes quantidades de coordenadas coletadas em campo.

---

# 🗺️ Marcadores no WME

Os pontos carregados podem ser representados visualmente no mapa.

A interface diferencia, por exemplo:

* 🔴 pontos pendentes;
* 🟢 pontos analisados;
* ⭐ ponto atualmente selecionado.

Dessa forma, o usuário consegue visualizar rapidamente o progresso do levantamento.

---

# 💾 Persistência do progresso

O progresso da análise é armazenado localmente no navegador utilizando `localStorage`.

Assim, o usuário pode fechar o navegador e continuar posteriormente sem necessariamente perder o estado dos pontos já analisados.

---

# 🔄 Fluxo completo do projeto

O fluxo principal pode ser representado assim:

```text
┌─────────────────────┐
│ Levantamento campo  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Marcador Android    │
│                     │
│ GPS/GNSS            │
│ Categorias          │
│ Pontos               │
└──────────┬──────────┘
           │
           ├───────────────┐
           │               │
           ▼               ▼
     Android Auto     Modo Flutuante
           │               │
           └───────┬───────┘
                   │
                   ▼
            Pontos salvos
                   │
                   ▼
             Exportação KML
                   │
                   ▼
          ┌─────────────────┐
          │  Locador WME    │
          └────────┬────────┘
                   │
                   ▼
          Análise no WME
                   │
                   ▼
          Marcar como analisado
```

---

# 🗂️ Estrutura do projeto

A estrutura atual do projeto inclui, entre outros:

```text
App_Marcador_waze/
│
├── Locador_WME_2.4.0.user.js
├── README.md
├── estrutura_projeto.txt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
│
└── app/
    ├── build.gradle
    └── src/
        └── main/
            ├── AndroidManifest.xml
            │
            ├── java/
            │   └── com/example/marcador/
            │       ├── MainActivity.kt
            │       ├── MarcadorCarAppService.kt
            │       ├── MarcadorCarSession.kt
            │       ├── MarcadorCarScreen.kt
            │       └── MarcadorFloatingService.kt
            │
            └── res/
                ├── drawable/
                ├── layout/
                ├── mipmap-*/
                ├── values/
                └── xml/
```

A estrutura pode evoluir conforme novas funcionalidades forem incorporadas.

---

# 🛠️ Tecnologias utilizadas

## Aplicativo Android

* Kotlin
* Android SDK
* AndroidX
* AndroidX Car App
* Android Location / GNSS APIs
* Foreground Service
* `TYPE_APPLICATION_OVERLAY`
* SharedPreferences
* JSON
* KML
* FileProvider

## Locador WME

* JavaScript
* Waze Map Editor SDK
* Tampermonkey / Violentmonkey
* DOMParser
* SVG
* localStorage

---

# 📋 Requisitos para desenvolvimento

Para desenvolver e compilar o aplicativo:

* Android Studio;
* Android SDK;
* JDK compatível com o projeto;
* dispositivo Android físico para testes de localização/GNSS;
* dispositivo compatível com Android Auto, caso queira testar a interface automotiva;
* navegador com Tampermonkey, Violentmonkey ou extensão compatível para testar o userscript.

---

# 🔨 Compilação

Clone o projeto:

```bash
git clone https://github.com/clenildo96/App_Marcador_waze.git
cd App_Marcador_waze
```

Para gerar uma versão de desenvolvimento:

```bash
./gradlew assembleDebug
```

No Windows:

```powershell
.\gradlew assembleDebug
```

O APK de desenvolvimento é gerado pelo Gradle na estrutura de build do projeto.

---

# 🔐 Versão Release

A versão Release utiliza assinatura digital Android.

A **keystore não deve ser armazenada no repositório público**.

Para gerar uma versão Release assinada, a configuração de assinatura deve ser feita localmente no ambiente de desenvolvimento.

Nunca publique:

* senhas da keystore;
* arquivos `.jks` ou `.keystore`;
* chaves privadas;
* tokens;
* credenciais;
* arquivos contendo informações secretas.

---

# 🔒 Privacidade

O aplicativo foi projetado para armazenar os dados localmente.

Categorias e pontos são mantidos no armazenamento local do Android.

Não existe, na arquitetura atual, um servidor próprio responsável por receber automaticamente as coordenadas.

A exportação e o compartilhamento dos dados são ações realizadas pelo usuário.

O Modo Flutuante utiliza permissões específicas do Android para funcionar sobre outros aplicativos.

---

# ⚠️ Permissões

O aplicativo pode utilizar permissões relacionadas a:

* localização precisa;
* localização aproximada;
* execução de serviço em primeiro plano;
* exibição sobre outros aplicativos.

As permissões são necessárias para as funcionalidades correspondentes do aplicativo.

---

# 🚧 Status do projeto

**Em desenvolvimento.**

Atualmente estão implementados:

* ✅ captura de coordenadas;
* ✅ categorias personalizáveis;
* ✅ armazenamento local;
* ✅ exportação JSON;
* ✅ exportação KML;
* ✅ permalink para o WME;
* ✅ Monitor GNSS;
* ✅ Sky View;
* ✅ integração com Android Auto;
* ✅ Modo Flutuante;
* ✅ registro de pontos através da interface flutuante;
* ✅ Locador WME;
* ✅ carregamento de KML no WME;
* ✅ navegação ponto a ponto;
* ✅ controle de pontos analisados;
* ✅ persistência do progresso no navegador.

O projeto continua recebendo melhorias conforme as necessidades identificadas durante o uso real.

---

# 🤝 Contribuições

Sugestões, testes, correções e melhorias são bem-vindos.

Antes de realizar alterações significativas, recomenda-se abrir uma discussão ou issue para alinhar a proposta.

---

# 👨‍💻 Autor

Desenvolvido por **Clenildo Teixeira** como uma ferramenta independente de apoio ao levantamento, organização e análise de coordenadas para utilização com o Waze Map Editor.

---

# ⚠️ Aviso

O **Marcador WME / Locador WME** é um projeto independente.

Não é um aplicativo oficial do Waze e não é desenvolvido, mantido ou endossado pelo Waze.

**Waze** e **Waze Map Editor** são marcas de seus respectivos proprietários.

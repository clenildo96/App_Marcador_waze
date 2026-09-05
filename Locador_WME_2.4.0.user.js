// ==UserScript==
// @name         Locador WME
// @namespace    LocadorWME
// @version      2.4.0
// @description  Exibe pontos de um KML no Waze Map Editor para análise individual.
// @author       Você
// @match        https://www.waze.com/*/editor*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=waze.com
// @grant        none
// @run-at       document-start
// ==/UserScript==

(function () {

    'use strict';

    const SCRIPT_ID = 'locador-wme';
    const SCRIPT_NAME = 'Locador WME';
    const LAYER_NAME = 'locador-wme-pontos';

    const STORAGE_KEY = 'locador_wme_progresso_v2';
    const PANEL_POSITION_KEY = 'locador_wme_painel_posicao_v1';
    const PANEL_HEIGHT_KEY = 'locador_wme_painel_altura_v2';

    const ZOOM_DO_PONTO = 20;
    const ALTURA_MINIMA = 180;

    let sdk = null;

    let pontos = [];
    let pontoAtual = -1;
    let kmlNome = '';

    let painel = null;

    let arrastando = false;
    let arrastoOffsetX = 0;
    let arrastoOffsetY = 0;

    let redimensionando = false;
    let alturaInicial = 0;
    let mouseInicialY = 0;

    /************************************************************
     * LOG
     ************************************************************/

    function log(...args) {
        console.log('[Locador WME]', ...args);
    }

    /************************************************************
     * SDK
     ************************************************************/

    function iniciarSDK() {

        /*
         * O WME pode executar o userscript antes do SDK existir.
         * Por isso aguardamos o SDK.
         */

        if (!window.SDK_INITIALIZED) {
            setTimeout(iniciarSDK, 500);
            return;
        }

        window.SDK_INITIALIZED
            .then(() => {

                try {

                    if (typeof window.getWmeSdk !== 'function') {
                        throw new Error(
                            'getWmeSdk não está disponível.'
                        );
                    }

                    sdk = window.getWmeSdk({
                        scriptId: SCRIPT_ID,
                        scriptName: SCRIPT_NAME
                    });

                    log('SDK inicializado.');

                    iniciar();

                }
                catch (erro) {

                    console.error(
                        '[Locador WME] Erro ao obter SDK:',
                        erro
                    );

                }

            })
            .catch(erro => {

                console.error(
                    '[Locador WME] Erro no SDK:',
                    erro
                );

            });

    }

    /************************************************************
     * INICIAR
     ************************************************************/

    function iniciar() {

        if (!sdk) {
            return;
        }

        criarCSS();
        criarPainel();
        criarCamada();

        log('Locador WME pronto.');

    }

    /************************************************************
     * CAMADA
     ************************************************************/

    function criarCamada() {

        try {

            sdk.Map.addLayer({

                layerName: LAYER_NAME,

                zIndexing: true,

                styleRules: [

                    /*
                     * PADRÃO — PENDENTE
                     */
                    {
                        style: {
                            externalGraphic:
                                criarMarcadorSVG(
                                    '#d32f2f',
                                    false
                                ),

                            graphicWidth: 30,
                            graphicHeight: 30,

                            graphicXOffset: -15,
                            graphicYOffset: -15,

                            fillOpacity: 1
                        }
                    },

                    /*
                     * ANALISADO
                     */
                    {
                        predicate: propriedades =>
                            propriedades.analisado === true,

                        style: {

                            externalGraphic:
                                criarMarcadorSVG(
                                    '#2e7d32',
                                    false
                                ),

                            graphicWidth: 30,
                            graphicHeight: 30,

                            graphicXOffset: -15,
                            graphicYOffset: -15,

                            fillOpacity: 1
                        }
                    },

                    /*
                     * PONTO ATUAL
                     */
                    {
                        predicate: propriedades =>
                            propriedades.locadorAtual === true,

                        style: {

                            externalGraphic:
                                criarMarcadorSVG(
                                    '#ff9800',
                                    true
                                ),

                            graphicWidth: 38,
                            graphicHeight: 38,

                            graphicXOffset: -19,
                            graphicYOffset: -19,

                            fillOpacity: 1
                        }
                    }

                ]

            });

        }
        catch (erro) {

            console.error(
                '[Locador WME] Erro criando camada:',
                erro
            );

        }

    }

    /************************************************************
     * SVG DOS MARCADORES
     ************************************************************/

    function criarMarcadorSVG(cor, estrela) {

        let conteudo;

        if (estrela) {

            conteudo = `
                <path
                    d="
                    M20 3
                    L24.2 13.1
                    L35 13.9
                    L26.7 20.9
                    L29.3 31.4
                    L20 25.8
                    L10.7 31.4
                    L13.3 20.9
                    L5 13.9
                    L15.8 13.1
                    Z
                    "
                    fill="${cor}"
                    stroke="#ffffff"
                    stroke-width="2.5"
                />
            `;

        }
        else {

            conteudo = `
                <circle
                    cx="20"
                    cy="20"
                    r="16"
                    fill="${cor}"
                    stroke="#ffffff"
                    stroke-width="3"
                />
            `;

        }

        const svg = `
            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="40"
                height="40"
                viewBox="0 0 40 40"
            >
                ${conteudo}
            </svg>
        `;

        return (
            'data:image/svg+xml;charset=UTF-8,' +
            encodeURIComponent(svg)
        );

    }

    /************************************************************
     * PAINEL
     ************************************************************/

    function criarPainel() {

        painel = document.createElement('div');

        painel.id = 'locador-wme-painel';

        painel.innerHTML = `

            <div
                id="locador-cabecalho"
                title="Arraste para mover"
            >

                <strong>
                    📍 LOCADOR WME
                </strong>

                <span id="locador-arrastar">
                    ⠿
                </span>

                <button
                    id="locador-minimizar"
                    title="Minimizar"
                    type="button"
                >
                    −
                </button>

            </div>

            <div id="locador-conteudo">

                <label class="locador-carregar">

                    📂 CARREGAR KML

                    <input
                        type="file"
                        id="locador-arquivo"
                        accept=".kml,.xml"
                    >

                </label>

                <div id="locador-status">
                    Nenhum KML carregado.
                </div>

                <div class="locador-contadores">

                    <div>
                        <b id="locador-total">0</b>
                        <span>TOTAL</span>
                    </div>

                    <div>
                        <b id="locador-analisados">0</b>
                        <span>ANALISADOS</span>
                    </div>

                    <div>
                        <b id="locador-pendentes">0</b>
                        <span>PENDENTES</span>
                    </div>

                </div>

                <div class="locador-lista-titulo">
                    PRÓXIMOS PONTOS
                </div>

                <div id="locador-lista">

                    <div class="locador-vazio">
                        Carregue um arquivo KML.
                    </div>

                </div>

                <div
                    id="locador-info"
                    class="locador-info"
                >
                </div>

                <div class="locador-botoes">

                    <button
                        id="locador-anterior"
                        type="button"
                        disabled
                        title="Ponto anterior"
                    >
                        ◀
                    </button>

                    <button
                        id="locador-ir"
                        type="button"
                        disabled
                    >
                        📍 IR AO PONTO
                    </button>

                    <button
                        id="locador-proximo"
                        type="button"
                        disabled
                        title="Próximo ponto"
                    >
                        ▶
                    </button>

                </div>

                <button
                    id="locador-marcar"
                    class="locador-marcar"
                    type="button"
                    disabled
                >
                    ☐ MARCAR COMO ANALISADO
                </button>

                <button
                    id="locador-limpar"
                    class="locador-limpar"
                    type="button"
                    disabled
                >
                    🗑 LIMPAR KML
                </button>

                <div class="locador-ajuda">

                    <b>Lista:</b>
                    clique em qualquer ponto para navegar.

                    <br><br>

                    🔴 Pendente

                    <br>

                    🟢 Analisado

                    <br>

                    ⭐ Ponto atual

                </div>

            </div>

            <div
                id="locador-resize"
                title="Arraste para alterar a altura"
            >
                ↕
            </div>

        `;

        document.body.appendChild(painel);

        /********************************************************
         * EVENTOS
         ********************************************************/

        document
            .getElementById('locador-arquivo')
            .addEventListener(
                'change',
                carregarKML
            );

        document
            .getElementById('locador-anterior')
            .addEventListener(
                'click',
                () => {

                    selecionarPonto(
                        pontoAtual - 1,
                        true
                    );

                }
            );

        document
            .getElementById('locador-proximo')
            .addEventListener(
                'click',
                () => {

                    selecionarPonto(
                        pontoAtual + 1,
                        true
                    );

                }
            );

        document
            .getElementById('locador-ir')
            .addEventListener(
                'click',
                () => {

                    irParaPonto(
                        pontoAtual
                    );

                }
            );

        document
            .getElementById('locador-marcar')
            .addEventListener(
                'click',
                marcarAnalisado
            );

        document
            .getElementById('locador-limpar')
            .addEventListener(
                'click',
                limparKML
            );

        document
            .getElementById('locador-minimizar')
            .addEventListener(
                'click',
                alternarPainel
            );

        configurarArrastar();
        configurarRedimensionamento();

        restaurarPosicaoPainel();
        restaurarAlturaPainel();

        document.addEventListener(
            'keydown',
            tratarTeclado
        );

    }

    /************************************************************
     * TECLADO
     ************************************************************/

    function tratarTeclado(event) {

        if (
            event.target instanceof HTMLInputElement ||
            event.target instanceof HTMLTextAreaElement ||
            event.target instanceof HTMLSelectElement
        ) {
            return;
        }

        if (
            event.key === 'ArrowLeft' &&
            pontoAtual > 0
        ) {

            event.preventDefault();

            selecionarPonto(
                pontoAtual - 1,
                true
            );

        }

        if (
            event.key === 'ArrowRight' &&
            pontoAtual < pontos.length - 1
        ) {

            event.preventDefault();

            selecionarPonto(
                pontoAtual + 1,
                true
            );

        }

    }

    /************************************************************
     * LISTA DOS 10
     ************************************************************/

    function atualizarLista() {

        const lista =
            document.getElementById(
                'locador-lista'
            );

        if (!lista) {
            return;
        }

        if (!pontos.length) {

            lista.innerHTML = `
                <div class="locador-vazio">
                    Carregue um arquivo KML.
                </div>
            `;

            return;

        }

        /*
         * Mostra até 10 pontos a partir do atual.
         *
         * Se estiver perto do final,
         * volta para que ainda existam 10 itens
         * quando possível.
         */

        let inicio =
            Math.max(
                0,
                pontoAtual
            );

        if (inicio + 10 > pontos.length) {

            inicio =
                Math.max(
                    0,
                    pontos.length - 10
                );

        }

        const fim =
            Math.min(
                pontos.length,
                inicio + 10
            );

        let html = '';

        for (
            let i = inicio;
            i < fim;
            i++
        ) {

            const ponto =
                pontos[i];

            const atual =
                i === pontoAtual;

            const classeEstado =
                ponto.analisado
                    ? 'analisado'
                    : 'pendente';

            html += `

                <div
                    class="
                        locador-item
                        ${atual ? 'atual' : ''}
                        ${classeEstado}
                    "
                    data-index="${i}"
                    role="button"
                    tabindex="0"
                    title="Clique para navegar ao ponto ${ponto.numero}"
                >

                    <div class="locador-item-numero">

                        ${
                            atual
                                ? '⭐'
                                : (
                                    ponto.analisado
                                        ? '✓'
                                        : '○'
                                )
                        }

                        ${ponto.numero}

                    </div>

                    <div class="locador-item-nome">

                        ${escapeHTML(
                            ponto.nome
                        )}

                    </div>

                    <div class="locador-item-coord">

                        ${ponto.latitude.toFixed(5)},
                        ${ponto.longitude.toFixed(5)}

                    </div>

                </div>

            `;

        }

        lista.innerHTML = html;

        /*
         * CLIQUE NA LISTA
         *
         * Este é o ponto principal da alteração:
         *
         * qualquer um dos 10 itens pode ser clicado
         * e o mapa será levado imediatamente para ele.
         */

        lista
            .querySelectorAll('.locador-item')
            .forEach(item => {

                item.addEventListener(
                    'click',
                    () => {

                        const index =
                            Number(
                                item.dataset.index
                            );

                        selecionarPonto(
                            index,
                            true
                        );

                    }
                );

                item.addEventListener(
                    'keydown',
                    event => {

                        if (
                            event.key === 'Enter' ||
                            event.key === ' '
                        ) {

                            event.preventDefault();

                            const index =
                                Number(
                                    item.dataset.index
                                );

                            selecionarPonto(
                                index,
                                true
                            );

                        }

                    }
                );

            });

    }

    /************************************************************
     * ARRASTAR PAINEL
     ************************************************************/

    function configurarArrastar() {

        const cabecalho =
            document.getElementById(
                'locador-cabecalho'
            );

        cabecalho.addEventListener(
            'mousedown',
            iniciarArrasto
        );

        document.addEventListener(
            'mousemove',
            moverPainel
        );

        document.addEventListener(
            'mouseup',
            finalizarArrasto
        );

    }

    function iniciarArrasto(event) {

        if (
            event.target.closest(
                '#locador-minimizar'
            )
        ) {
            return;
        }

        if (
            event.target.closest(
                '#locador-resize'
            )
        ) {
            return;
        }

        if (redimensionando) {
            return;
        }

        arrastando = true;

        const rect =
            painel.getBoundingClientRect();

        arrastoOffsetX =
            event.clientX -
            rect.left;

        arrastoOffsetY =
            event.clientY -
            rect.top;

        painel.style.right = 'auto';
        painel.style.bottom = 'auto';

        painel.style.cursor = 'grabbing';

        document.body.style.userSelect =
            'none';

    }

    function moverPainel(event) {

        if (!arrastando) {
            return;
        }

        let esquerda =
            event.clientX -
            arrastoOffsetX;

        let topo =
            event.clientY -
            arrastoOffsetY;

        const largura =
            painel.offsetWidth;

        esquerda =
            Math.max(
                -largura + 80,
                Math.min(
                    window.innerWidth - 80,
                    esquerda
                )
            );

        topo =
            Math.max(
                40,
                Math.min(
                    window.innerHeight - 40,
                    topo
                )
            );

        painel.style.left =
            `${esquerda}px`;

        painel.style.top =
            `${topo}px`;

    }

    function finalizarArrasto() {

        if (!arrastando) {
            return;
        }

        arrastando = false;

        painel.style.cursor =
            'default';

        document.body.style.userSelect =
            '';

        salvarPosicaoPainel();

    }

    /************************************************************
     * REDIMENSIONAR ALTURA
     ************************************************************/

    function configurarRedimensionamento() {

        const controle =
            document.getElementById(
                'locador-resize'
            );

        controle.addEventListener(
            'mousedown',
            iniciarRedimensionamento
        );

        document.addEventListener(
            'mousemove',
            redimensionarPainel
        );

        document.addEventListener(
            'mouseup',
            finalizarRedimensionamento
        );

    }

    function iniciarRedimensionamento(event) {

        event.preventDefault();
        event.stopPropagation();

        redimensionando = true;

        alturaInicial =
            painel.offsetHeight;

        mouseInicialY =
            event.clientY;

        document.body.style.userSelect =
            'none';

        document.body.style.cursor =
            'ns-resize';

    }

    function redimensionarPainel(event) {

        if (!redimensionando) {
            return;
        }

        /*
         * Arrastar para cima:
         * aumenta a altura.
         *
         * Arrastar para baixo:
         * diminui a altura.
         */

        const diferenca =
            event.clientY -
            mouseInicialY;

        let novaAltura =
            alturaInicial +
            diferenca;

        const alturaMaxima =
            Math.max(
                ALTURA_MINIMA + 100,
                window.innerHeight - 50
            );

        novaAltura =
            Math.max(
                ALTURA_MINIMA,
                Math.min(
                    alturaMaxima,
                    novaAltura
                )
            );

        painel.style.height =
            `${novaAltura}px`;

    }

    function finalizarRedimensionamento() {

        if (!redimensionando) {
            return;
        }

        redimensionando = false;

        document.body.style.userSelect =
            '';

        document.body.style.cursor =
            '';

        salvarAlturaPainel();

    }

    /************************************************************
     * ALTURA
     ************************************************************/

    function salvarAlturaPainel() {

        if (!painel) {
            return;
        }

        localStorage.setItem(
            PANEL_HEIGHT_KEY,
            String(
                painel.offsetHeight
            )
        );

    }

    function restaurarAlturaPainel() {

        try {

            const valor =
                localStorage.getItem(
                    PANEL_HEIGHT_KEY
                );

            if (!valor) {
                return;
            }

            const altura =
                Number(valor);

            if (
                Number.isFinite(altura) &&
                altura >= ALTURA_MINIMA
            ) {

                painel.style.height =
                    `${altura}px`;

            }

        }
        catch (erro) {

            console.warn(
                '[Locador WME] Erro restaurando altura:',
                erro
            );

        }

    }

    /************************************************************
     * POSIÇÃO
     ************************************************************/

    function salvarPosicaoPainel() {

        if (!painel) {
            return;
        }

        const rect =
            painel.getBoundingClientRect();

        localStorage.setItem(

            PANEL_POSITION_KEY,

            JSON.stringify({

                left:
                    rect.left,

                top:
                    rect.top

            })

        );

    }

    function restaurarPosicaoPainel() {

        try {

            const salvo =
                localStorage.getItem(
                    PANEL_POSITION_KEY
                );

            if (!salvo) {
                return;
            }

            const dados =
                JSON.parse(
                    salvo
                );

            if (
                typeof dados.left !== 'number' ||
                typeof dados.top !== 'number'
            ) {
                return;
            }

            painel.style.right = 'auto';
            painel.style.bottom = 'auto';

            painel.style.left =
                `${dados.left}px`;

            painel.style.top =
                `${dados.top}px`;

        }
        catch (erro) {

            console.warn(
                '[Locador WME] Erro restaurando posição:',
                erro
            );

        }

    }

    /************************************************************
     * MINIMIZAR
     ************************************************************/

    function alternarPainel() {

        painel.classList.toggle(
            'minimizado'
        );

        const botao =
            document.getElementById(
                'locador-minimizar'
            );

        botao.textContent =
            painel.classList.contains(
                'minimizado'
            )
                ? '+'
                : '−';

    }

    /************************************************************
     * CARREGAR KML
     ************************************************************/

    function carregarKML(event) {

        const arquivo =
            event.target.files[0];

        if (!arquivo) {
            return;
        }

        kmlNome =
            arquivo.name;

        const leitor =
            new FileReader();

        leitor.onload =
            evento => {

                try {

                    lerKML(
                        evento.target.result
                    );

                }
                catch (erro) {

                    console.error(
                        '[Locador WME]',
                        erro
                    );

                    alert(
                        'Erro ao ler o KML:\n\n' +
                        erro.message
                    );

                }

            };

        leitor.readAsText(
            arquivo
        );

    }

    /************************************************************
     * LER KML
     ************************************************************/

    function lerKML(texto) {

        const parser =
            new DOMParser();

        const xml =
            parser.parseFromString(
                texto,
                'application/xml'
            );

        if (
            xml.querySelector(
                'parsererror'
            )
        ) {

            throw new Error(
                'XML/KML inválido.'
            );

        }

        const placemarks =
            Array.from(
                xml.getElementsByTagName(
                    'Placemark'
                )
            );

        const novosPontos = [];

        placemarks.forEach(
            (placemark, index) => {

                const coordenadas =
                    placemark
                        .getElementsByTagName(
                            'coordinates'
                        )[0];

                if (!coordenadas) {
                    return;
                }

                const textoCoords =
                    coordenadas.textContent
                        .trim();

                if (!textoCoords) {
                    return;
                }

                const primeiro =
                    textoCoords
                        .split(
                            /[\s\r\n]+/
                        )[0];

                const partes =
                    primeiro.split(',');

                const longitude =
                    Number(
                        partes[0]
                    );

                const latitude =
                    Number(
                        partes[1]
                    );

                const altitude =
                    partes.length >= 3
                        ? Number(
                            partes[2]
                        )
                        : 0;

                if (
                    !Number.isFinite(latitude) ||
                    !Number.isFinite(longitude)
                ) {
                    return;
                }

                const nome =
                    obterTexto(
                        placemark,
                        'name'
                    );

                const descricao =
                    obterTexto(
                        placemark,
                        'description'
                    );

                novosPontos.push({

                    id:
                        `ponto-${index}-${Date.now()}`,

                    numero:
                        novosPontos.length + 1,

                    nome:
                        nome ||
                        'Sem nome',

                    descricao:
                        descricao ||
                        '',

                    latitude,

                    longitude,

                    altitude,

                    analisado:
                        false

                });

            }
        );

        if (!novosPontos.length) {

            throw new Error(
                'Nenhuma coordenada encontrada no KML.'
            );

        }

        pontos =
            novosPontos;

        pontoAtual =
            0;

        restaurarProgresso();

        desenharPontos();

        atualizarInterface();

        atualizarLista();

        document
            .getElementById(
                'locador-status'
            )
            .textContent =
                `${kmlNome} — ${pontos.length} pontos carregados.`;

        document
            .getElementById(
                'locador-limpar'
            )
            .disabled =
                false;

        /*
         * Vai automaticamente ao primeiro ponto.
         */

        irParaPonto(0);

    }

    /************************************************************
     * TEXTO
     ************************************************************/

    function obterTexto(
        elemento,
        tag
    ) {

        const encontrado =
            elemento
                .getElementsByTagName(
                    tag
                )[0];

        return encontrado
            ? encontrado.textContent.trim()
            : '';

    }

    /************************************************************
     * DESENHAR
     ************************************************************/

    function desenharPontos() {

        if (!sdk) {
            return;
        }

        try {

            sdk.Map.removeAllFeaturesFromLayer({

                layerName:
                    LAYER_NAME

            });

        }
        catch (erro) {

            console.warn(
                '[Locador WME] Não foi possível limpar camada:',
                erro
            );

        }

        if (!pontos.length) {
            return;
        }

        const features =
            pontos.map(
                (ponto, index) => {

                    return {

                        id:
                            ponto.id,

                        type:
                            'Feature',

                        geometry: {

                            type:
                                'Point',

                            coordinates: [

                                ponto.longitude,
                                ponto.latitude

                            ]

                        },

                        properties: {

                            numero:
                                ponto.numero,

                            nome:
                                ponto.nome,

                            analisado:
                                ponto.analisado === true,

                            locadorAtual:
                                index === pontoAtual

                        }

                    };

                }
            );

        try {

            sdk.Map.addFeaturesToLayer({

                layerName:
                    LAYER_NAME,

                features:
                    features

            });

            sdk.Map.redrawLayer({

                layerName:
                    LAYER_NAME

            });

        }
        catch (erro) {

            console.error(
                '[Locador WME] Erro desenhando:',
                erro
            );

        }

    }

    /************************************************************
     * SELECIONAR PONTO
     ************************************************************/

    function selecionarPonto(
        index,
        irMapa = false
    ) {

        if (
            index < 0 ||
            index >= pontos.length
        ) {
            return;
        }

        pontoAtual =
            index;

        atualizarInterface();

        atualizarLista();

        desenharPontos();

        /*
         * Qualquer seleção originada da lista,
         * das setas ou do teclado navega para o ponto.
         */

        if (irMapa) {

            irParaPonto(
                index
            );

        }

    }

    /************************************************************
     * IR AO PONTO
     ************************************************************/

    function irParaPonto(index) {

        if (
            !sdk ||
            index < 0 ||
            index >= pontos.length
        ) {
            return;
        }

        const ponto =
            pontos[index];

        try {

            /*
             * SDK OFICIAL DO WME
             *
             * setMapCenter aceita:
             *
             * {
             *     lonLat: {
             *         lon,
             *         lat
             *     },
             *     zoomLevel
             * }
             */

            sdk.Map.setMapCenter({

                lonLat: {

                    lon:
                        Number(
                            ponto.longitude
                        ),

                    lat:
                        Number(
                            ponto.latitude
                        )

                },

                zoomLevel:
                    ZOOM_DO_PONTO

            });

            log(
                'Navegando para ponto:',
                ponto.numero,
                ponto.latitude,
                ponto.longitude
            );

        }
        catch (erro) {

            console.error(
                '[Locador WME] Erro indo ao ponto:',
                erro
            );

        }

    }

    /************************************************************
     * MARCAR ANALISADO
     ************************************************************/

    function marcarAnalisado() {

        if (
            pontoAtual < 0 ||
            !pontos[pontoAtual]
        ) {
            return;
        }

        const ponto =
            pontos[pontoAtual];

        ponto.analisado =
            !ponto.analisado;

        salvarProgresso();

        atualizarInterface();

        atualizarLista();

        desenharPontos();

        /*
         * Depois de analisar,
         * vai automaticamente para o próximo ponto.
         */

        if (
            ponto.analisado &&
            pontoAtual <
                pontos.length - 1
        ) {

            pontoAtual++;

            atualizarInterface();

            atualizarLista();

            desenharPontos();

            irParaPonto(
                pontoAtual
            );

        }

    }

    /************************************************************
     * INTERFACE
     ************************************************************/

    function atualizarInterface() {

        const total =
            pontos.length;

        const analisados =
            pontos.filter(
                p =>
                    p.analisado === true
            ).length;

        const pendentes =
            total -
            analisados;

        const totalEl =
            document.getElementById(
                'locador-total'
            );

        const analisadosEl =
            document.getElementById(
                'locador-analisados'
            );

        const pendentesEl =
            document.getElementById(
                'locador-pendentes'
            );

        if (totalEl) {
            totalEl.textContent =
                total;
        }

        if (analisadosEl) {
            analisadosEl.textContent =
                analisados;
        }

        if (pendentesEl) {
            pendentesEl.textContent =
                pendentes;
        }

        const anterior =
            document.getElementById(
                'locador-anterior'
            );

        const proximo =
            document.getElementById(
                'locador-proximo'
            );

        const ir =
            document.getElementById(
                'locador-ir'
            );

        const marcar =
            document.getElementById(
                'locador-marcar'
            );

        if (anterior) {

            anterior.disabled =
                pontoAtual <= 0;

        }

        if (proximo) {

            proximo.disabled =
                pontoAtual < 0 ||
                pontoAtual >=
                    total - 1;

        }

        if (ir) {

            ir.disabled =
                pontoAtual < 0;

        }

        if (marcar) {

            marcar.disabled =
                pontoAtual < 0;

        }

        if (
            pontoAtual < 0 ||
            !pontos[pontoAtual]
        ) {

            return;

        }

        const ponto =
            pontos[pontoAtual];

        const info =
            document.getElementById(
                'locador-info'
            );

        if (!info) {
            return;
        }

        info.innerHTML = `

            <div class="locador-numero">

                PONTO
                ${ponto.numero}
                /
                ${total}

            </div>

            <div class="locador-tipo">

                ${escapeHTML(
                    ponto.nome
                )}

            </div>

            <div class="locador-linha">

                <span>Latitude</span>

                <b>
                    ${ponto.latitude.toFixed(7)}
                </b>

            </div>

            <div class="locador-linha">

                <span>Longitude</span>

                <b>
                    ${ponto.longitude.toFixed(7)}
                </b>

            </div>

            <div
                class="
                    locador-estado
                    ${
                        ponto.analisado
                            ? 'analisado'
                            : 'pendente'
                    }
                "
            >

                ${
                    ponto.analisado
                        ? '✓ ANALISADO'
                        : '○ PENDENTE'
                }

            </div>

        `;

        if (marcar) {

            marcar.textContent =
                ponto.analisado
                    ? '☑ DESMARCAR COMO ANALISADO'
                    : '☐ MARCAR COMO ANALISADO';

        }

    }

    /************************************************************
     * PROGRESSO
     ************************************************************/

    function salvarProgresso() {

        if (
            !kmlNome ||
            !pontos.length
        ) {
            return;
        }

        localStorage.setItem(

            STORAGE_KEY,

            JSON.stringify({

                arquivo:
                    kmlNome,

                pontos:
                    pontos.map(
                        ponto => ({

                            latitude:
                                ponto.latitude,

                            longitude:
                                ponto.longitude,

                            analisado:
                                ponto.analisado

                        })
                    )

            })

        );

    }

    function restaurarProgresso() {

        try {

            const salvo =
                localStorage.getItem(
                    STORAGE_KEY
                );

            if (!salvo) {
                return;
            }

            const dados =
                JSON.parse(
                    salvo
                );

            if (
                !dados ||
                !Array.isArray(
                    dados.pontos
                )
            ) {
                return;
            }

            pontos.forEach(
                ponto => {

                    const antigo =
                        dados.pontos.find(
                            p =>

                                Math.abs(
                                    p.latitude -
                                    ponto.latitude
                                ) <
                                0.00000001

                                &&

                                Math.abs(
                                    p.longitude -
                                    ponto.longitude
                                ) <
                                0.00000001
                        );

                    if (
                        antigo &&
                        antigo.analisado
                    ) {

                        ponto.analisado =
                            true;

                    }

                }
            );

        }
        catch (erro) {

            console.warn(
                '[Locador WME] Erro restaurando progresso:',
                erro
            );

        }

    }

    /************************************************************
     * LIMPAR
     ************************************************************/

    function limparKML() {

        if (!pontos.length) {
            return;
        }

        if (
            !confirm(
                'Remover todos os pontos e o progresso salvo?'
            )
        ) {
            return;
        }

        pontos = [];

        pontoAtual = -1;

        kmlNome = '';

        try {

            sdk.Map.removeAllFeaturesFromLayer({

                layerName:
                    LAYER_NAME

            });

        }
        catch (erro) {}

        localStorage.removeItem(
            STORAGE_KEY
        );

        document
            .getElementById(
                'locador-status'
            )
            .textContent =
                'Nenhum KML carregado.';

        atualizarInterface();

        atualizarLista();

        document
            .getElementById(
                'locador-limpar'
            )
            .disabled =
                true;

    }

    /************************************************************
     * ESCAPE HTML
     ************************************************************/

    function escapeHTML(texto) {

        return String(texto)

            .replace(
                /&/g,
                '&amp;'
            )

            .replace(
                /</g,
                '&lt;'
            )

            .replace(
                />/g,
                '&gt;'
            )

            .replace(
                /"/g,
                '&quot;'
            )

            .replace(
                /'/g,
                '&#039;'
            );

    }

    /************************************************************
     * CSS
     ************************************************************/

    function criarCSS() {

        const style =
            document.createElement(
                'style'
            );

        style.textContent = `

            #locador-wme-painel {

                position: fixed;

                top: 75px;
                right: 20px;

                width: 350px;

                height: 650px;

                min-height: ${ALTURA_MINIMA}px;

                min-width: 300px;

                max-height:
                    calc(100vh - 50px);

                z-index: 999999;

                background: #ffffff;

                border-radius: 10px;

                box-shadow:
                    0 5px 25px
                    rgba(0,0,0,.35);

                font-family:
                    Arial,
                    sans-serif;

                color: #222;

                overflow: hidden;

                display: flex;

                flex-direction: column;

            }

            #locador-cabecalho {

                flex:
                    0 0 46px;

                display:
                    flex;

                align-items:
                    center;

                padding:
                    0 10px;

                background:
                    #263238;

                color:
                    #ffffff;

                font-size:
                    14px;

                cursor:
                    grab;

                user-select:
                    none;

            }

            #locador-cabecalho strong {

                flex:
                    1;

            }

            #locador-arrastar {

                margin-right:
                    8px;

                opacity:
                    .6;

                font-size:
                    18px;

            }

            #locador-minimizar {

                border:
                    0;

                background:
                    transparent;

                color:
                    white;

                font-size:
                    22px;

                cursor:
                    pointer;

                width:
                    30px;

                height:
                    30px;

            }

            #locador-conteudo {

                flex:
                    1;

                min-height:
                    0;

                overflow-y:
                    auto;

                padding:
                    12px;

            }

            #locador-wme-painel.minimizado {

                height:
                    46px;

            }

            #locador-wme-painel.minimizado
            #locador-conteudo {

                display:
                    none;

            }

            .locador-carregar {

                display:
                    block;

                padding:
                    11px;

                border-radius:
                    6px;

                background:
                    #1976d2;

                color:
                    white;

                text-align:
                    center;

                font-weight:
                    bold;

                font-size:
                    12px;

                cursor:
                    pointer;

            }

            .locador-carregar input {

                display:
                    none;

            }

            #locador-status {

                margin-top:
                    8px;

                padding:
                    8px;

                background:
                    #f4f4f4;

                border-radius:
                    6px;

                font-size:
                    11px;

                word-break:
                    break-word;

            }

            .locador-contadores {

                display:
                    grid;

                grid-template-columns:
                    repeat(3,1fr);

                gap:
                    5px;

                margin-top:
                    8px;

            }

            .locador-contadores div {

                background:
                    #f5f5f5;

                border-radius:
                    6px;

                padding:
                    7px 3px;

                text-align:
                    center;

            }

            .locador-contadores b {

                display:
                    block;

                font-size:
                    18px;

            }

            .locador-contadores span {

                font-size:
                    8px;

                color:
                    #777;

            }

            .locador-lista-titulo {

                margin-top:
                    10px;

                margin-bottom:
                    5px;

                font-size:
                    10px;

                font-weight:
                    bold;

                color:
                    #555;

            }

            #locador-lista {

                border:
                    1px solid #ddd;

                border-radius:
                    6px;

                overflow:
                    hidden;

            }

            .locador-item {

                display:
                    grid;

                grid-template-columns:
                    35px 1fr;

                grid-template-rows:
                    22px 18px;

                column-gap:
                    6px;

                padding:
                    5px 7px;

                border-bottom:
                    1px solid #eeeeee;

                cursor:
                    pointer;

                transition:
                    background .1s;

                outline:
                    none;

            }

            .locador-item:last-child {

                border-bottom:
                    0;

            }

            .locador-item:hover {

                background:
                    #e3f2fd;

            }

            .locador-item:focus {

                background:
                    #e3f2fd;

                box-shadow:
                    inset 0 0 0 2px #1976d2;

            }

            .locador-item.atual {

                background:
                    #fff3cd;

                box-shadow:
                    inset 4px 0 0 #ff9800;

            }

            .locador-item-numero {

                grid-row:
                    1 / 3;

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    center;

                font-weight:
                    bold;

                font-size:
                    12px;

            }

            .locador-item-nome {

                overflow:
                    hidden;

                text-overflow:
                    ellipsis;

                white-space:
                    nowrap;

                font-size:
                    11px;

                font-weight:
                    bold;

            }

            .locador-item-coord {

                font-size:
                    9px;

                color:
                    #888;

            }

            .locador-item.pendente
            .locador-item-numero {

                color:
                    #d32f2f;

            }

            .locador-item.analisado
            .locador-item-numero {

                color:
                    #2e7d32;

            }

            .locador-info {

                margin-top:
                    9px;

            }

            .locador-numero {

                font-size:
                    18px;

                font-weight:
                    bold;

                margin-bottom:
                    5px;

            }

            .locador-tipo {

                display:
                    inline-block;

                max-width:
                    100%;

                overflow:
                    hidden;

                text-overflow:
                    ellipsis;

                white-space:
                    nowrap;

                background:
                    #1976d2;

                color:
                    white;

                padding:
                    4px 7px;

                border-radius:
                    4px;

                font-size:
                    10px;

                font-weight:
                    bold;

            }

            .locador-linha {

                display:
                    flex;

                justify-content:
                    space-between;

                border-bottom:
                    1px solid #eee;

                padding:
                    5px 0;

                font-size:
                    10px;

            }

            .locador-linha span {

                color:
                    #777;

            }

            .locador-estado {

                margin-top:
                    7px;

                padding:
                    7px;

                border-radius:
                    5px;

                text-align:
                    center;

                font-size:
                    10px;

                font-weight:
                    bold;

            }

            .locador-estado.pendente {

                background:
                    #ffebee;

                color:
                    #c62828;

            }

            .locador-estado.analisado {

                background:
                    #e8f5e9;

                color:
                    #2e7d32;

            }

            .locador-botoes {

                display:
                    grid;

                grid-template-columns:
                    45px 1fr 45px;

                gap:
                    5px;

                margin-top:
                    8px;

            }

            .locador-botoes button,
            .locador-marcar,
            .locador-limpar {

                border:
                    0;

                border-radius:
                    6px;

                padding:
                    9px;

                cursor:
                    pointer;

                font-size:
                    11px;

                font-weight:
                    bold;

            }

            .locador-botoes button {

                background:
                    #eceff1;

            }

            #locador-ir {

                background:
                    #1976d2;

                color:
                    white;

            }

            .locador-marcar {

                width:
                    100%;

                margin-top:
                    6px;

                background:
                    #43a047;

                color:
                    white;

            }

            .locador-limpar {

                width:
                    100%;

                margin-top:
                    6px;

                background:
                    #eeeeee;

            }

            button:disabled {

                opacity:
                    .45;

                cursor:
                    not-allowed;

            }

            .locador-ajuda {

                margin-top:
                    8px;

                padding:
                    7px;

                background:
                    #fff8e1;

                border-radius:
                    6px;

                font-size:
                    9px;

                line-height:
                    1.4;

            }

            #locador-resize {

                position:
                    absolute;

                right:
                    2px;

                bottom:
                    2px;

                width:
                    22px;

                height:
                    22px;

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    center;

                color:
                    #777;

                font-size:
                    14px;

                cursor:
                    ns-resize;

                user-select:
                    none;

                z-index:
                    10;

            }

            .locador-vazio {

                padding:
                    20px 8px;

                text-align:
                    center;

                color:
                    #999;

                font-size:
                    10px;

            }

        `;

        document.head.appendChild(
            style
        );

    }

    /************************************************************
     * EXECUTAR
     ************************************************************/

    /*
     * Como o @run-at é document-start, o SDK pode ainda não
     * existir. A função iniciarSDK() aguarda até ele aparecer.
     *
     * Isso também segue a recomendação da documentação do WME
     * para scripts executados antes do DOM/SDK estar disponível.
     */

    iniciarSDK();

})();
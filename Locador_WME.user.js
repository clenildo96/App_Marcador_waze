// ==UserScript==
// @name         Locador WME
// @namespace    LocadorWME
// @version      3.0.2
// @description  Levantamento de campo integrado ao WME SDK.
// @author       Clenildo
// @match        https://www.waze.com/*/editor*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=waze.com
// @updateURL    https://raw.githubusercontent.com/clenildo96/App_Marcador_waze/main/Locador_WME.user.js
// @downloadURL  https://raw.githubusercontent.com/clenildo96/App_Marcador_waze/main/Locador_WME.user.js
// @grant        none
// @run-at       document-start
// ==/UserScript==

(function () {

    'use strict';

    /******************************************************************
     * CONFIGURAÇÃO
     ******************************************************************/

    const SCRIPT_ID = 'locador-wme';
    const SCRIPT_NAME = 'Locador WME';

    const LAYER_NAME = 'locador-wme-pontos';
    const LAYER_CHECKBOX_NAME = 'Locador — Pontos de campo';

    const STORAGE_KEY = 'locador_wme_progresso_v4';
    const UI_STATE_KEY = 'locador_wme_ui_v3';

    const ZOOM_DO_PONTO = 20;

    let sdk = null;

    let pontos = [];
    let pontoAtual = -1;

    let kmlNome = '';

    let tabLabel = null;
    let tabPane = null;
    let painel = null;

    let camadaCriada = false;
    let eventosCamadaAtivos = false;
    let checkboxCriado = false;


    /******************************************************************
     * LOG
     ******************************************************************/

    function log(...args) {

        console.log(
            '[Locador WME]',
            ...args
        );

    }


    function logErro(...args) {

        console.error(
            '[Locador WME]',
            ...args
        );

    }


    /******************************************************************
     * SDK
     ******************************************************************/

    function iniciarSDK() {

        if (!window.SDK_INITIALIZED) {

            setTimeout(
                iniciarSDK,
                500
            );

            return;

        }


        window.SDK_INITIALIZED
            .then(() => {

                try {

                    if (
                        typeof window.getWmeSdk !==
                        'function'
                    ) {

                        throw new Error(
                            'getWmeSdk não está disponível.'
                        );

                    }


                    sdk =
                        window.getWmeSdk({

                            scriptId:
                                SCRIPT_ID,

                            scriptName:
                                SCRIPT_NAME

                        });


                    log(
                        'SDK inicializado:',
                        sdk.getSDKVersion?.()
                            ||
                        'versão desconhecida'
                    );


                    iniciar();

                }
                catch (erro) {

                    logErro(
                        'Erro ao obter SDK:',
                        erro
                    );

                }

            })
            .catch(erro => {

                logErro(
                    'Erro no SDK:',
                    erro
                );

            });

    }


    /******************************************************************
     * INICIAR
     ******************************************************************/

    async function iniciar() {

        if (!sdk) {
            return;
        }


        criarCSS();

        await criarSidebar();

        criarCamada();

        criarLayerSwitcher();

        configurarEventosSDK();


        log(
            'Locador WME 3.0.2 pronto.'
        );

    }


    /******************************************************************
     * SIDEBAR WME
     ******************************************************************/

    async function criarSidebar() {

        try {

            const resultado =
                await sdk.Sidebar.registerScriptTab();


            tabLabel =
                resultado.tabLabel;


            tabPane =
                resultado.tabPane;


            tabLabel.textContent =
                '📍 Locador';


            tabLabel.title =
                'Locador — levantamento de campo';


            tabPane.innerHTML =
                '';


            painel =
                document.createElement(
                    'div'
                );


            painel.id =
                'locador-wme-painel';


            painel.innerHTML =
                criarHTMLPainel();


            tabPane.appendChild(
                painel
            );


            configurarEventosPainel();

            restaurarEstadoUI();


            log(
                'Aba Locador registrada no Sidebar.'
            );

        }
        catch (erro) {

            logErro(
                'Não foi possível criar a aba do Sidebar:',
                erro
            );


            criarPainelFallback();

        }

    }


    /******************************************************************
     * HTML DO PAINEL
     ******************************************************************/

    function criarHTMLPainel() {

        return `

            <div class="locador-container">

                <div class="locador-header">

                    <div class="locador-title">

                        <span class="locador-title-icon">
                            📍
                        </span>

                        <span>
                            LOCADOR WME
                        </span>

                    </div>

                    <div class="locador-version">
                        3.0.2
                    </div>

                </div>


                <label class="locador-carregar">

                    <span>
                        📂 CARREGAR KML / KMZ
                    </span>

                    <input
                        type="file"
                        id="locador-arquivo"
                        accept=".kml,.xml,.kmz"
                    >

                </label>


                <div
                    id="locador-status"
                    class="locador-status"
                >

                    Nenhum levantamento carregado.

                </div>


                <div class="locador-contadores">

                    <div class="locador-contador">

                        <b id="locador-total">
                            0
                        </b>

                        <span>
                            TOTAL
                        </span>

                    </div>


                    <div class="locador-contador">

                        <b id="locador-analisados">
                            0
                        </b>

                        <span>
                            ANALISADOS
                        </span>

                    </div>


                    <div class="locador-contador">

                        <b id="locador-pendentes">
                            0
                        </b>

                        <span>
                            PENDENTES
                        </span>

                    </div>

                </div>


                <div class="locador-secao-titulo">

                    <span>
                        PRÓXIMOS PONTOS
                    </span>

                    <span
                        id="locador-progresso"
                        class="locador-progresso"
                    >
                        0%
                    </span>

                </div>


                <div
                    id="locador-lista"
                    class="locador-lista"
                >

                    <div class="locador-vazio">

                        📂 Carregue um KML/KMZ

                    </div>

                </div>


                <div
                    id="locador-info"
                    class="locador-info"
                >
                </div>


                <div class="locador-navegacao">

                    <button
                        id="locador-anterior"
                        title="Ponto anterior"
                        disabled
                    >
                        ◀
                    </button>


                    <button
                        id="locador-ir"
                        class="locador-ir"
                        disabled
                    >
                        📍 IR AO PONTO
                    </button>


                    <button
                        id="locador-proximo"
                        title="Próximo ponto"
                        disabled
                    >
                        ▶
                    </button>

                </div>


                <button
                    id="locador-marcar"
                    class="locador-btn analisado"
                    disabled
                >
                    ☐ MARCAR COMO ANALISADO
                </button>


                <button
                    id="locador-limpar"
                    class="locador-btn limpar"
                    disabled
                >
                    🗑 LIMPAR LEVANTAMENTO
                </button>


                <div class="locador-legenda">

                    <div>
                        <span
                            class="legenda-ponto vermelho"
                        ></span>
                        Pendente
                    </div>

                    <div>
                        <span
                            class="legenda-ponto verde"
                        ></span>
                        Analisado
                    </div>

                    <div>
                        <span
                            class="legenda-ponto laranja"
                        ></span>
                        Atual
                    </div>

                </div>


                <div class="locador-ajuda">

                    <b>💡 Dica</b>

                    <br>

                    Clique diretamente em um ponto
                    no mapa para selecioná-lo.

                    <br><br>

                    🔴 Pendente

                    &nbsp;&nbsp;

                    🟢 Analisado

                    <br>

                    ⭐ Ponto atual

                    <br><br>

                    Passe o mouse sobre um ponto
                    no mapa para ver suas propriedades.

                </div>

            </div>

        `;

    }


    /******************************************************************
     * FALLBACK
     ******************************************************************/

    function criarPainelFallback() {

        painel =
            document.createElement(
                'div'
            );


        painel.id =
            'locador-wme-painel-fallback';


        painel.innerHTML =
            criarHTMLPainel();


        document.body.appendChild(
            painel
        );


        configurarEventosPainel();

    }


    /******************************************************************
     * EVENTOS DO PAINEL
     ******************************************************************/

    function configurarEventosPainel() {

        const arquivo =
            document.getElementById(
                'locador-arquivo'
            );


        if (arquivo) {

            arquivo.addEventListener(
                'change',
                carregarArquivo
            );

        }


        const anterior =
            document.getElementById(
                'locador-anterior'
            );


        if (anterior) {

            anterior.addEventListener(
                'click',
                () => {

                    selecionarPonto(
                        pontoAtual - 1,
                        true
                    );

                }
            );

        }


        const proximo =
            document.getElementById(
                'locador-proximo'
            );


        if (proximo) {

            proximo.addEventListener(
                'click',
                () => {

                    selecionarPonto(
                        pontoAtual + 1,
                        true
                    );

                }
            );

        }


        const ir =
            document.getElementById(
                'locador-ir'
            );


        if (ir) {

            ir.addEventListener(
                'click',
                () => {

                    irParaPonto(
                        pontoAtual
                    );

                }
            );

        }


        const marcar =
            document.getElementById(
                'locador-marcar'
            );


        if (marcar) {

            marcar.addEventListener(
                'click',
                marcarAnalisado
            );

        }


        const limpar =
            document.getElementById(
                'locador-limpar'
            );


        if (limpar) {

            limpar.addEventListener(
                'click',
                limparKML
            );

        }


        document.addEventListener(
            'keydown',
            tratarTeclado
        );

    }


    /******************************************************************
     * CAMADA SDK
     *
     * IMPORTANTE:
     * Aqui permanece o mesmo modelo da versão que você enviou.
     * O label NÃO recebe a função diretamente.
     * Ele usa styleContext -> getLabel.
     ******************************************************************/

    function criarCamada() {

        if (!sdk) {
            return;
        }


        if (camadaCriada) {
            return;
        }


        try {

            sdk.Map.addLayer({

                layerName:
                    LAYER_NAME,

                zIndexing:
                    true,


                styleContext: {

                    /**************************************************
                     * ÍCONE
                     **************************************************/

                    getGraphic:
                        ({ feature }) => {

                            return criarIconeSVG(
                                feature?.properties
                            );

                        },


                    /**************************************************
                     * RÓTULO
                     **************************************************/

                    getLabel:
                        ({ feature }) => {

                            return criarRotulo(
                                feature?.properties
                            );

                        },


                    /**************************************************
                     * TOOLTIP
                     *
                     * Mostra TODAS as propriedades do ponto.
                     **************************************************/

                    getTitle:
                        ({ feature }) => {

                            const p =
                                feature?.properties;


                            if (!p) {

                                return 'Locador';

                            }


                            return Object.entries(p)

                                .filter(
                                    ([chave, valor]) =>

                                        valor !== null
                                        &&
                                        valor !== undefined
                                        &&
                                        valor !== ''

                                )

                                .map(
                                    ([chave, valor]) =>

                                        `${chave}: ${valor}`

                                )

                                .join('\n');

                        },


                    /**************************************************
                     * COR DO RÓTULO
                     *
                     * ANALISADO = VERDE
                     **************************************************/

                    getFontColor:
                        ({ feature }) => {

                            const p =
                                feature?.properties;


                            /*
                             * Se analisado, fica VERDE.
                             * Isso tem prioridade sobre o ponto atual.
                             */

                            if (
                                p?.analisado === true
                            ) {

                                return '#2e7d32';

                            }


                            /*
                             * Ponto atual pendente.
                             */

                            if (
                                p?.locadorAtual === true
                            ) {

                                return '#e65100';

                            }


                            /*
                             * Pendente.
                             */

                            return '#b71c1c';

                        },


                    /**************************************************
                     * TAMANHO
                     **************************************************/

                    getFontSize:
                        ({ feature }) => {

                            const p =
                                feature?.properties;


                            return p?.locadorAtual
                                ? '13px'
                                : '11px';

                        }

                },


                /****************************************************
                 * ESTILOS
                 ****************************************************/

                styleRules: [

                    /*
                     * ESTILO PADRÃO
                     */
                    {

                        style: {

                            externalGraphic:
                                '${getGraphic}',

                            graphicWidth:
                                32,

                            graphicHeight:
                                32,

                            graphicXOffset:
                                -16,

                            graphicYOffset:
                                -16,

                            fillOpacity:
                                1,


                            /*
                             * O RÓTULO ORIGINAL
                             */
                            label:
                                '${getLabel}',

                            labelAlign:
                                'cm',

                            labelXOffset:
                                0,

                            labelYOffset:
                                -28,


                            fontColor:
                                '${getFontColor}',

                            fontFamily:
                                'Arial, sans-serif',

                            fontWeight:
                                'bold',

                            fontSize:
                                '${getFontSize}',


                            /*
                             * Contorno branco.
                             *
                             * Isso mantém o rótulo legível
                             * sobre o mapa.
                             */
                            labelOutlineColor:
                                '#ffffff',

                            labelOutlineWidth:
                                4,

                            labelOutlineOpacity:
                                0.95,


                            /*
                             * TOOLTIP
                             */
                            title:
                                '${getTitle}',


                            pointerEvents:
                                'visiblePainted',

                            cursor:
                                'pointer'

                        }

                    },


                    /*
                     * PONTO ATUAL
                     *
                     * SOMENTE se ainda NÃO estiver analisado.
                     */
                    {

                        predicate:
                            properties =>

                                properties.locadorAtual === true
                                &&
                                properties.analisado !== true,


                        style: {

                            graphicWidth:
                                42,

                            graphicHeight:
                                42,

                            graphicXOffset:
                                -21,

                            graphicYOffset:
                                -21,

                            labelYOffset:
                                -34,

                            fontSize:
                                '13px',

                            labelOutlineWidth:
                                5

                        }

                    }

                ]

            });


            camadaCriada =
                true;


            log(
                'Camada criada:',
                LAYER_NAME
            );

        }
        catch (erro) {

            logErro(
                'Erro criando camada:',
                erro
            );

        }

    }


    /******************************************************************
     * LAYER SWITCHER
     ******************************************************************/

    function criarLayerSwitcher() {

        if (!sdk) {
            return;
        }


        if (checkboxCriado) {
            return;
        }


        try {

            sdk.LayerSwitcher.addLayerCheckbox({

                name:
                    LAYER_CHECKBOX_NAME,

                isChecked:
                    true

            });


            checkboxCriado =
                true;


            log(
                'Checkbox do Layer Switcher criado.'
            );

        }
        catch (erro) {

            logErro(
                'Erro criando checkbox:',
                erro
            );

        }

    }


    /******************************************************************
     * EVENTOS SDK
     ******************************************************************/

    function configurarEventosSDK() {

        if (!sdk) {
            return;
        }


        /**************************************************************
         * Clique na camada
         **************************************************************/

        try {

            sdk.Events.trackLayerEvents({

                layerName:
                    LAYER_NAME

            });


            eventosCamadaAtivos =
                true;

        }
        catch (erro) {

            logErro(
                'Erro ativando eventos da camada:',
                erro
            );

        }


        /**************************************************************
         * Clique no ponto
         **************************************************************/

        sdk.Events.on({

            eventName:
                'wme-layer-feature-clicked',

            eventHandler:
                evento => {

                    if (
                        evento.layerName !==
                        LAYER_NAME
                    ) {

                        return;

                    }


                    const id =
                        String(
                            evento.featureId
                        );


                    const index =
                        pontos.findIndex(
                            ponto =>

                                String(
                                    ponto.id
                                ) === id
                        );


                    if (
                        index === -1
                    ) {

                        return;

                    }


                    selecionarPonto(
                        index,
                        false
                    );


                    /*
                     * Mantém o comportamento
                     * da versão original:
                     * centraliza no ponto.
                     */

                    irParaPonto(
                        index
                    );

                }

        });


        /**************************************************************
         * VISIBILIDADE DA CAMADA
         **************************************************************/

        sdk.Events.on({

            eventName:
                'wme-layer-checkbox-toggled',

            eventHandler:
                evento => {

                    if (
                        evento.name !==
                        LAYER_CHECKBOX_NAME
                    ) {

                        return;

                    }


                    try {

                        sdk.Map.setLayerVisibility({

                            layerName:
                                LAYER_NAME,

                            visibility:
                                evento.checked

                        });

                    }
                    catch (erro) {

                        logErro(
                            'Erro alterando visibilidade:',
                            erro
                        );

                    }

                }

        });

    }


    /******************************************************************
     * ÍCONE SVG
     ******************************************************************/

    function criarIconeSVG(
        propriedades = {}
    ) {

        const categoria =
            normalizarCategoria(
                propriedades.nome
            );


        const atual =
            propriedades.locadorAtual === true;


        const analisado =
            propriedades.analisado === true;


        /*
         * Cores originais por categoria.
         */

        let cor =
            '#d32f2f';


        if (
            categoria === 'quebra-mola'
        ) {

            cor =
                '#f57c00';

        }
        else if (
            categoria === 'radar'
        ) {

            cor =
                '#7b1fa2';

        }
        else if (
            categoria === 'posto'
        ) {

            cor =
                '#1976d2';

        }
        else if (
            categoria === 'perigo'
        ) {

            cor =
                '#c62828';

        }


        /*
         * Analisado:
         * marcador verde.
         */

        if (
            analisado
        ) {

            cor =
                '#2e7d32';

        }


        /*
         * Ponto atual pendente:
         * laranja.
         */

        if (
            atual &&
            !analisado
        ) {

            cor =
                '#ff9800';

        }


        const simbolo =
            obterSimboloCategoria(
                categoria
            );


        const raio =
            atual && !analisado
                ? 18
                : 15;


        const svg = `

            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="40"
                height="40"
                viewBox="0 0 40 40"
            >

                ${
                    atual && !analisado
                        ? `

                            <circle
                                cx="20"
                                cy="20"
                                r="18"
                                fill="#ffffff"
                                opacity=".95"
                            />

                        `
                        : ''
                }


                <circle
                    cx="20"
                    cy="20"
                    r="${raio}"
                    fill="${cor}"
                    stroke="#ffffff"
                    stroke-width="${
                        atual && !analisado
                            ? 3
                            : 2.5
                    }"
                />


                <text
                    x="20"
                    y="25"
                    text-anchor="middle"
                    font-family="Arial, sans-serif"
                    font-size="${
                        simbolo.length > 1
                            ? 11
                            : 17
                    }"
                    font-weight="bold"
                    fill="#ffffff"
                >
                    ${escapeXML(simbolo)}
                </text>

            </svg>

        `;


        return (

            'data:image/svg+xml;charset=UTF-8,' +

            encodeURIComponent(
                svg
            )

        );

    }


    /******************************************************************
     * SÍMBOLO DO ÍCONE
     ******************************************************************/

    function obterSimboloCategoria(
        categoria
    ) {

        switch (
            categoria
        ) {

            case 'quebra-mola':
                return 'QM';

            case 'radar':
                return 'R';

            case 'posto':
                return 'P';

            case 'perigo':
                return '!';

            default:
                return '•';

        }

    }


    /******************************************************************
     * CATEGORIA
     ******************************************************************/

    function normalizarCategoria(
        nome
    ) {

        const texto =
            String(
                nome || ''
            )
                .normalize('NFD')
                .replace(
                    /[\u0300-\u036f]/g,
                    ''
                )
                .toLowerCase()
                .trim();


        if (
            texto.includes('quebra')
            ||
            texto.includes('mola')
        ) {

            return 'quebra-mola';

        }


        if (
            texto.includes('radar')
        ) {

            return 'radar';

        }


        if (
            texto.includes('posto')
        ) {

            return 'posto';

        }


        if (
            texto.includes('perigo')
        ) {

            return 'perigo';

        }


        return 'outro';

    }


    /******************************************************************
     * RÓTULO
     *
     * ÚNICA alteração desejada:
     *
     * Pendente:
     * 🚧 QUEBRA-MOLA #37
     *
     * Analisado:
     * ✅ 🚧 QUEBRA-MOLA #37
     ******************************************************************/

    function criarRotulo(
        propriedades = {}
    ) {

        const numero =
            propriedades.numero ?? '';


        const nome =
            propriedades.nome ||
            'Ponto';


        const categoria =
            categoriaBonita(
                nome
            );


        /*
         * SOMENTE adicionamos o OK quando
         * o ponto já foi analisado.
         */

        const status =
            propriedades.analisado === true
                ? '✅ '
                : '';


        return (

            `${status}` +

            `${simboloTextoCategoria(
                categoria
            )} ` +

            `${categoria} #${numero}`

        );

    }


    /******************************************************************
     * NOME DA CATEGORIA
     ******************************************************************/

    function categoriaBonita(
        nome
    ) {

        const categoria =
            normalizarCategoria(
                nome
            );


        switch (
            categoria
        ) {

            case 'quebra-mola':
                return 'QUEBRA-MOLA';

            case 'radar':
                return 'RADAR';

            case 'posto':
                return 'POSTO';

            case 'perigo':
                return 'PERIGO';

            default:

                return String(
                    nome ||
                    'PONTO'
                )
                    .substring(
                        0,
                        30
                    )
                    .toUpperCase();

        }

    }


    /******************************************************************
     * EMOJI DA CATEGORIA
     ******************************************************************/

    function simboloTextoCategoria(
        categoria
    ) {

        if (
            categoria ===
            'QUEBRA-MOLA'
        ) {

            return '🚧';

        }


        if (
            categoria ===
            'RADAR'
        ) {

            return '📡';

        }


        if (
            categoria ===
            'POSTO'
        ) {

            return '⛽';

        }


        if (
            categoria ===
            'PERIGO'
        ) {

            return '⚠️';

        }


        return '📍';

    }


    /******************************************************************
     * CARREGAR ARQUIVO
     ******************************************************************/

    async function carregarArquivo(
        event
    ) {

        const arquivo =
            event.target.files[0];


        if (!arquivo) {
            return;
        }


        try {

            atualizarStatus(
                `Lendo ${arquivo.name}...`
            );


            const nome =
                arquivo.name.toLowerCase();


            let texto;


            if (
                nome.endsWith('.kmz')
            ) {

                texto =
                    await lerKMZ(
                        arquivo
                    );

            }
            else {

                texto =
                    await arquivo.text();

            }


            kmlNome =
                arquivo.name;


            lerKML(
                texto
            );

        }
        catch (erro) {

            logErro(
                'Erro lendo arquivo:',
                erro
            );


            alert(
                'Erro ao ler o arquivo:\n\n' +
                erro.message
            );


            atualizarStatus(
                'Erro ao carregar arquivo.'
            );

        }

    }


    /******************************************************************
     * LEITOR KMZ
     ******************************************************************/

    async function lerKMZ(
        arquivo
    ) {

        const buffer =
            await arquivo.arrayBuffer();


        const bytes =
            new Uint8Array(
                buffer
            );


        let offset =
            0;


        while (
            offset + 30 <=
            bytes.length
        ) {

            const assinatura =
                lerUint32(
                    bytes,
                    offset
                );


            /*
             * Local file header.
             */

            if (
                assinatura ===
                0x04034b50
            ) {

                const metodo =
                    lerUint16(
                        bytes,
                        offset + 8
                    );


                const tamanhoComprimido =
                    lerUint32(
                        bytes,
                        offset + 18
                    );


                const tamanhoNome =
                    lerUint16(
                        bytes,
                        offset + 26
                    );


                const tamanhoExtra =
                    lerUint16(
                        bytes,
                        offset + 28
                    );


                const nomeBytes =
                    bytes.slice(
                        offset + 30,
                        offset +
                        30 +
                        tamanhoNome
                    );


                const nomeEntrada =
                    new TextDecoder()
                        .decode(
                            nomeBytes
                        );


                const inicioDados =
                    offset +
                    30 +
                    tamanhoNome +
                    tamanhoExtra;


                const fimDados =
                    inicioDados +
                    tamanhoComprimido;


                if (
                    nomeEntrada
                        .toLowerCase()
                        .endsWith('.kml')
                ) {

                    const dados =
                        bytes.slice(
                            inicioDados,
                            fimDados
                        );


                    let dadosKML;


                    if (
                        metodo === 0
                    ) {

                        dadosKML =
                            dados;

                    }
                    else if (
                        metodo === 8
                    ) {

                        if (
                            typeof DecompressionStream !==
                            'function'
                        ) {

                            throw new Error(
                                'Este navegador não oferece DecompressionStream para abrir KMZ.'
                            );

                        }


                        const stream =
                            new Blob([
                                dados
                            ])
                                .stream()
                                .pipeThrough(
                                    new DecompressionStream(
                                        'deflate-raw'
                                    )
                                );


                        const arrayBuffer =
                            await new Response(
                                stream
                            ).arrayBuffer();


                        dadosKML =
                            new Uint8Array(
                                arrayBuffer
                            );

                    }
                    else {

                        throw new Error(
                            `Método ZIP ${metodo} não suportado no KMZ.`
                        );

                    }


                    return new TextDecoder(
                        'utf-8'
                    ).decode(
                        dadosKML
                    );

                }


                offset =
                    fimDados;


                continue;

            }


            /*
             * Central directory.
             */

            if (
                assinatura ===
                0x02014b50
            ) {

                break;

            }


            offset++;

        }


        throw new Error(
            'Nenhum arquivo KML foi encontrado dentro do KMZ.'
        );

    }


    function lerUint16(
        bytes,
        offset
    ) {

        return (

            bytes[offset] |

            (
                bytes[offset + 1]
                << 8
            )

        );

    }


    function lerUint32(
        bytes,
        offset
    ) {

        return (

            bytes[offset] |

            (
                bytes[offset + 1]
                << 8
            ) |

            (
                bytes[offset + 2]
                << 16
            ) |

            (
                bytes[offset + 3]
                << 24
            )

        ) >>> 0;

    }


    /******************************************************************
     * LER KML
     ******************************************************************/

    function lerKML(
        texto
    ) {

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


        const novosPontos =
            [];


        placemarks.forEach(
            (
                placemark,
                index
            ) => {

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
                    !Number.isFinite(
                        latitude
                    )
                    ||
                    !Number.isFinite(
                        longitude
                    )
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


        if (
            !novosPontos.length
        ) {

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


        atualizarStatus(
            `${kmlNome} — ${pontos.length} pontos carregados.`
        );


        habilitarBotoes();


        irParaPonto(
            0
        );

    }


    /******************************************************************
     * TEXTO
     ******************************************************************/

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


    /******************************************************************
     * DESENHAR PONTOS
     ******************************************************************/

    function desenharPontos() {

        if (
            !sdk ||
            !camadaCriada
        ) {

            return;

        }


        try {

            sdk.Map.removeAllFeaturesFromLayer({

                layerName:
                    LAYER_NAME

            });

        }
        catch (erro) {

            logErro(
                'Erro limpando camada:',
                erro
            );

        }


        if (
            !pontos.length
        ) {

            return;

        }


        const features =
            pontos.map(
                (
                    ponto,
                    index
                ) => {

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

                            descricao:
                                ponto.descricao,

                            latitude:
                                ponto.latitude,

                            longitude:
                                ponto.longitude,

                            altitude:
                                ponto.altitude,

                            analisado:
                                ponto.analisado === true,

                            locadorAtual:
                                index === pontoAtual,

                            categoria:
                                normalizarCategoria(
                                    ponto.nome
                                )

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

            logErro(
                'Erro desenhando pontos:',
                erro
            );

        }

    }


    /******************************************************************
     * SELECIONAR PONTO
     ******************************************************************/

    function selecionarPonto(
        index,
        irMapa = false
    ) {

        if (
            index < 0
            ||
            index >= pontos.length
        ) {

            return;

        }


        pontoAtual =
            index;


        atualizarInterface();

        atualizarLista();

        desenharPontos();


        if (
            irMapa
        ) {

            irParaPonto(
                index
            );

        }

    }


    /******************************************************************
     * IR AO PONTO
     ******************************************************************/

    function irParaPonto(
        index
    ) {

        if (
            !sdk
            ||
            index < 0
            ||
            index >= pontos.length
        ) {

            return;

        }


        const ponto =
            pontos[index];


        try {

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

        }
        catch (erro) {

            logErro(
                'Erro indo ao ponto:',
                erro
            );

        }

    }


    /******************************************************************
     * MARCAR ANALISADO
     ******************************************************************/

    function marcarAnalisado() {

        if (
            pontoAtual < 0
            ||
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
         * Mantém o comportamento da versão original:
         * marca e vai para o próximo.
         */

        if (
            ponto.analisado
            &&
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


    /******************************************************************
     * INTERFACE
     ******************************************************************/

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


        const progresso =
            total > 0
                ? Math.round(
                    (
                        analisados /
                        total
                    ) * 100
                )
                : 0;


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


        const progressoEl =
            document.getElementById(
                'locador-progresso'
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


        if (progressoEl) {

            progressoEl.textContent =
                `${progresso}%`;

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
                pontoAtual < 0
                ||
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
            pontoAtual < 0
            ||
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


        const categoria =
            categoriaBonita(
                ponto.nome
            );


        info.innerHTML = `

            <div class="locador-ponto-cabecalho">

                <div>

                    PONTO
                    ${ponto.numero}
                    /
                    ${total}

                </div>

                <span class="locador-badge">

                    ${escapeHTML(
                        categoria
                    )}

                </span>

            </div>


            <div class="locador-nome">

                ${escapeHTML(
                    ponto.nome
                )}

            </div>


            <div class="locador-linha">

                <span>
                    Latitude
                </span>

                <b>
                    ${ponto.latitude.toFixed(7)}
                </b>

            </div>


            <div class="locador-linha">

                <span>
                    Longitude
                </span>

                <b>
                    ${ponto.longitude.toFixed(7)}
                </b>

            </div>


            <div class="locador-linha">

                <span>
                    Altitude
                </span>

                <b>
                    ${
                        Number.isFinite(
                            Number(
                                ponto.altitude
                            )
                        )
                            ? Number(
                                ponto.altitude
                            ).toFixed(2)
                            : '—'
                    }
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


    /******************************************************************
     * LISTA
     ******************************************************************/

    function atualizarLista() {

        const lista =
            document.getElementById(
                'locador-lista'
            );


        if (!lista) {
            return;
        }


        if (
            !pontos.length
        ) {

            lista.innerHTML = `

                <div class="locador-vazio">

                    📂 Carregue um KML/KMZ

                </div>

            `;

            return;

        }


        let inicio =
            Math.max(
                0,
                pontoAtual
            );


        if (
            inicio + 10 >
            pontos.length
        ) {

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


        let html =
            '';


        for (
            let i = inicio;
            i < fim;
            i++
        ) {

            const ponto =
                pontos[i];


            const atual =
                i === pontoAtual;


            const categoria =
                categoriaBonita(
                    ponto.nome
                );


            html += `

                <div
                    class="
                        locador-item
                        ${atual ? 'atual' : ''}
                        ${
                            ponto.analisado
                                ? 'analisado'
                                : 'pendente'
                        }
                    "
                    data-index="${i}"
                    role="button"
                    tabindex="0"
                >

                    <div class="item-numero">

                        ${
                            ponto.analisado
                                ? '✓'
                                : (
                                    atual
                                        ? '⭐'
                                        : '○'
                                )
                        }

                        ${ponto.numero}

                    </div>


                    <div class="item-conteudo">

                        <div class="item-nome">

                            ${simboloTextoCategoria(
                                categoria
                            )}

                            ${escapeHTML(
                                categoria
                            )}

                        </div>


                        <div class="item-coord">

                            ${ponto.latitude.toFixed(5)},
                            ${ponto.longitude.toFixed(5)}

                        </div>

                    </div>

                </div>

            `;

        }


        lista.innerHTML =
            html;


        lista
            .querySelectorAll(
                '.locador-item'
            )
            .forEach(
                item => {

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
                                event.key ===
                                'Enter'
                                ||
                                event.key ===
                                ' '
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

                }
            );

    }


    /******************************************************************
     * HABILITAR BOTÕES
     ******************************************************************/

    function habilitarBotoes() {

        const limpar =
            document.getElementById(
                'locador-limpar'
            );


        if (limpar) {

            limpar.disabled =
                false;

        }

    }


    /******************************************************************
     * STATUS
     ******************************************************************/

    function atualizarStatus(
        texto
    ) {

        const elemento =
            document.getElementById(
                'locador-status'
            );


        if (elemento) {

            elemento.textContent =
                texto;

        }

    }


    /******************************************************************
     * LIMPAR
     ******************************************************************/

    function limparKML() {

        if (
            !pontos.length
        ) {

            return;

        }


        if (
            !confirm(
                'Remover todos os pontos e o progresso salvo?'
            )
        ) {

            return;

        }


        pontos =
            [];


        pontoAtual =
            -1;


        kmlNome =
            '';


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


        atualizarStatus(
            'Nenhum levantamento carregado.'
        );


        atualizarInterface();

        atualizarLista();


        const limpar =
            document.getElementById(
                'locador-limpar'
            );


        if (limpar) {

            limpar.disabled =
                true;

        }

    }


    /******************************************************************
     * PROGRESSO
     ******************************************************************/

    function salvarProgresso() {

        if (
            !kmlNome
            ||
            !pontos.length
        ) {

            return;

        }


        try {

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
        catch (erro) {

            logErro(
                'Erro salvando progresso:',
                erro
            );

        }

    }


    /******************************************************************
     * RESTAURAR PROGRESSO
     ******************************************************************/

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
                !dados
                ||
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
                                    Number(
                                        p.latitude
                                    ) -
                                    Number(
                                        ponto.latitude
                                    )
                                ) <
                                0.00000001

                                &&

                                Math.abs(
                                    Number(
                                        p.longitude
                                    ) -
                                    Number(
                                        ponto.longitude
                                    )
                                ) <
                                0.00000001
                        );


                    if (
                        antigo
                        &&
                        antigo.analisado === true
                    ) {

                        ponto.analisado =
                            true;

                    }

                }
            );

        }
        catch (erro) {

            logErro(
                'Erro restaurando progresso:',
                erro
            );

        }

    }


    /******************************************************************
     * TECLADO
     ******************************************************************/

    function tratarTeclado(
        event
    ) {

        if (
            event.target instanceof
                HTMLInputElement
            ||
            event.target instanceof
                HTMLTextAreaElement
            ||
            event.target instanceof
                HTMLSelectElement
        ) {

            return;

        }


        if (
            event.key ===
            'ArrowLeft'
            &&
            pontoAtual > 0
        ) {

            event.preventDefault();


            selecionarPonto(
                pontoAtual - 1,
                true
            );

        }


        if (
            event.key ===
            'ArrowRight'
            &&
            pontoAtual <
            pontos.length - 1
        ) {

            event.preventDefault();


            selecionarPonto(
                pontoAtual + 1,
                true
            );

        }

    }


    /******************************************************************
     * ESTADO DA UI
     ******************************************************************/

    function salvarEstadoUI() {

        try {

            localStorage.setItem(

                UI_STATE_KEY,

                JSON.stringify({

                    painel:
                        true

                })

            );

        }
        catch (erro) {}

    }


    function restaurarEstadoUI() {

        try {

            localStorage.getItem(
                UI_STATE_KEY
            );

        }
        catch (erro) {}

    }


    /******************************************************************
     * ESCAPE HTML
     ******************************************************************/

    function escapeHTML(
        texto
    ) {

        return String(
            texto
        )

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


    /******************************************************************
     * ESCAPE XML
     ******************************************************************/

    function escapeXML(
        texto
    ) {

        return String(
            texto
        )

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
                '&apos;'
            );

    }


    /******************************************************************
     * CSS
     ******************************************************************/

    function criarCSS() {

        const style =
            document.createElement(
                'style'
            );


        style.textContent = `

            /*
             * ========================================================
             * CONTAINER
             * ========================================================
             */

            #locador-wme-painel {

                font-family:
                    Arial,
                    Helvetica,
                    sans-serif;

                color:
                    #263238;

            }


            .locador-container {

                padding:
                    10px;

            }


            /*
             * ========================================================
             * HEADER
             * ========================================================
             */

            .locador-header {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    space-between;

                padding:
                    10px 12px;

                margin-bottom:
                    9px;

                border-radius:
                    8px;

                background:
                    linear-gradient(
                        135deg,
                        #263238,
                        #37474f
                    );

                color:
                    #ffffff;

                box-shadow:
                    0 2px 6px
                    rgba(
                        0,
                        0,
                        0,
                        .18
                    );

            }


            .locador-title {

                display:
                    flex;

                align-items:
                    center;

                gap:
                    6px;

                font-size:
                    13px;

                font-weight:
                    700;

                letter-spacing:
                    .3px;

            }


            .locador-title-icon {

                font-size:
                    16px;

            }


            .locador-version {

                padding:
                    3px 6px;

                border-radius:
                    10px;

                background:
                    rgba(
                        255,
                        255,
                        255,
                        .13
                    );

                font-size:
                    8px;

                color:
                    #cfd8dc;

            }


            /*
             * ========================================================
             * CARREGAR
             * ========================================================
             */

            .locador-carregar {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    center;

                width:
                    100%;

                box-sizing:
                    border-box;

                padding:
                    9px;

                border-radius:
                    7px;

                background:
                    #1976d2;

                color:
                    #ffffff;

                cursor:
                    pointer;

                font-size:
                    10px;

                font-weight:
                    700;

                transition:
                    .15s ease;

            }


            .locador-carregar:hover {

                filter:
                    brightness(
                        .94
                    );

                transform:
                    translateY(
                        -1px
                    );

            }


            .locador-carregar input {

                display:
                    none;

            }


            /*
             * ========================================================
             * STATUS
             * ========================================================
             */

            .locador-status {

                margin-top:
                    7px;

                padding:
                    7px 8px;

                border:
                    1px solid #e0e0e0;

                border-radius:
                    6px;

                background:
                    #fafafa;

                color:
                    #616161;

                font-size:
                    9px;

                word-break:
                    break-word;

            }


            /*
             * ========================================================
             * CONTADORES
             * ========================================================
             */

            .locador-contadores {

                display:
                    grid;

                grid-template-columns:
                    repeat(
                        3,
                        1fr
                    );

                gap:
                    5px;

                margin-top:
                    7px;

            }


            .locador-contador {

                padding:
                    7px 3px;

                border:
                    1px solid #eeeeee;

                border-radius:
                    6px;

                background:
                    #fafafa;

                text-align:
                    center;

            }


            .locador-contador b {

                display:
                    block;

                font-size:
                    17px;

                line-height:
                    18px;

            }


            .locador-contador span {

                display:
                    block;

                margin-top:
                    2px;

                font-size:
                    7px;

                color:
                    #757575;

                font-weight:
                    700;

            }


            /*
             * ========================================================
             * SEÇÃO
             * ========================================================
             */

            .locador-secao-titulo {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    space-between;

                margin-top:
                    10px;

                margin-bottom:
                    5px;

                font-size:
                    9px;

                font-weight:
                    700;

                color:
                    #455a64;

            }


            .locador-progresso {

                padding:
                    2px 6px;

                border-radius:
                    8px;

                background:
                    #e8f5e9;

                color:
                    #2e7d32;

                font-size:
                    8px;

            }


            /*
             * ========================================================
             * LISTA
             * ========================================================
             */

            .locador-lista {

                overflow:
                    hidden;

                border:
                    1px solid #e0e0e0;

                border-radius:
                    7px;

                background:
                    #ffffff;

            }


            .locador-item {

                display:
                    grid;

                grid-template-columns:
                    38px 1fr;

                padding:
                    6px;

                border-bottom:
                    1px solid #eeeeee;

                cursor:
                    pointer;

                transition:
                    .12s ease;

            }


            .locador-item:last-child {

                border-bottom:
                    0;

            }


            .locador-item:hover {

                background:
                    #e3f2fd;

            }


            .locador-item.atual {

                background:
                    #fff8e1;

                box-shadow:
                    inset 4px 0 0
                    #ff9800;

            }


            .locador-item.analisado {

                background:
                    #f5fbf6;

            }


            .locador-item.analisado:hover {

                background:
                    #e8f5e9;

            }


            .item-numero {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    center;

                font-size:
                    11px;

                font-weight:
                    700;

            }


            .locador-item.analisado
            .item-numero {

                color:
                    #2e7d32;

            }


            .locador-item.pendente
            .item-numero {

                color:
                    #c62828;

            }


            .item-conteudo {

                min-width:
                    0;

            }


            .item-nome {

                overflow:
                    hidden;

                white-space:
                    nowrap;

                text-overflow:
                    ellipsis;

                font-size:
                    10px;

                font-weight:
                    700;

            }


            .item-coord {

                margin-top:
                    2px;

                color:
                    #9e9e9e;

                font-size:
                    8px;

            }


            /*
             * ========================================================
             * INFORMAÇÕES DO PONTO
             * ========================================================
             */

            .locador-info {

                margin-top:
                    7px;

                padding:
                    9px;

                border:
                    1px solid #e0e0e0;

                border-radius:
                    7px;

                background:
                    #fafafa;

            }


            .locador-ponto-cabecalho {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    space-between;

                font-size:
                    11px;

                font-weight:
                    700;

            }


            .locador-badge {

                max-width:
                    55%;

                overflow:
                    hidden;

                padding:
                    3px 6px;

                border-radius:
                    5px;

                background:
                    #1976d2;

                color:
                    #ffffff;

                white-space:
                    nowrap;

                text-overflow:
                    ellipsis;

                font-size:
                    7px;

            }


            .locador-nome {

                margin:
                    6px 0;

                font-size:
                    10px;

                font-weight:
                    700;

                color:
                    #263238;

            }


            .locador-linha {

                display:
                    flex;

                align-items:
                    center;

                justify-content:
                    space-between;

                padding:
                    4px 0;

                border-bottom:
                    1px solid #eeeeee;

                font-size:
                    8px;

            }


            .locador-linha span {

                color:
                    #757575;

            }


            .locador-linha b {

                text-align:
                    right;

            }


            .locador-estado {

                margin-top:
                    7px;

                padding:
                    6px;

                border-radius:
                    5px;

                text-align:
                    center;

                font-size:
                    8px;

                font-weight:
                    700;

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


            /*
             * ========================================================
             * NAVEGAÇÃO
             * ========================================================
             */

            .locador-navegacao {

                display:
                    grid;

                grid-template-columns:
                    40px 1fr 40px;

                gap:
                    5px;

                margin-top:
                    7px;

            }


            .locador-navegacao button {

                border:
                    0;

                border-radius:
                    6px;

                padding:
                    8px;

                background:
                    #eceff1;

                color:
                    #37474f;

                cursor:
                    pointer;

                font-size:
                    10px;

                font-weight:
                    700;

                transition:
                    .15s ease;

            }


            .locador-navegacao button:hover:not(:disabled) {

                transform:
                    translateY(
                        -1px
                    );

                filter:
                    brightness(
                        .96
                    );

            }


            .locador-navegacao
            .locador-ir {

                background:
                    #1976d2;

                color:
                    #ffffff;

            }


            /*
             * ========================================================
             * BOTÕES
             * ========================================================
             */

            .locador-btn {

                width:
                    100%;

                margin-top:
                    5px;

                border:
                    0;

                border-radius:
                    6px;

                padding:
                    8px;

                cursor:
                    pointer;

                font-size:
                    9px;

                font-weight:
                    700;

                transition:
                    .15s ease;

            }


            .locador-btn:hover:not(:disabled) {

                transform:
                    translateY(
                        -1px
                    );

                filter:
                    brightness(
                        .96
                    );

            }


            .locador-btn.analisado {

                background:
                    #43a047;

                color:
                    #ffffff;

            }


            .locador-btn.limpar {

                background:
                    #eeeeee;

                color:
                    #424242;

            }


            .locador-btn:disabled,
            .locador-navegacao button:disabled {

                opacity:
                    .45;

                cursor:
                    not-allowed;

            }


            /*
             * ========================================================
             * LEGENDA
             * ========================================================
             */

            .locador-legenda {

                display:
                    grid;

                grid-template-columns:
                    repeat(
                        3,
                        1fr
                    );

                gap:
                    4px;

                margin-top:
                    8px;

                color:
                    #616161;

                font-size:
                    7px;

            }


            .locador-legenda div {

                display:
                    flex;

                align-items:
                    center;

            }


            .legenda-ponto {

                display:
                    inline-block;

                width:
                    8px;

                height:
                    8px;

                margin-right:
                    3px;

                border-radius:
                    50%;

            }


            .legenda-ponto.vermelho {

                background:
                    #d32f2f;

            }


            .legenda-ponto.verde {

                background:
                    #2e7d32;

            }


            .legenda-ponto.laranja {

                background:
                    #ff9800;

            }


            /*
             * ========================================================
             * AJUDA
             * ========================================================
             */

            .locador-ajuda {

                margin-top:
                    8px;

                padding:
                    7px;

                border:
                    1px solid #ffe0a3;

                border-radius:
                    6px;

                background:
                    #fff8e1;

                color:
                    #6d4c41;

                font-size:
                    8px;

                line-height:
                    1.45;

            }


            .locador-vazio {

                padding:
                    18px 8px;

                color:
                    #9e9e9e;

                text-align:
                    center;

                font-size:
                    9px;

            }

        `;


        document.head.appendChild(
            style
        );

    }


    /******************************************************************
     * EXECUTAR
     ******************************************************************/

    iniciarSDK();

})();

package com.example.marcador

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * ============================
 * PALETA DE CORES DAS CATEGORIAS
 * ============================
 * Paleta fixa oferecida ao usuário na tela de Configurações para
 * personalizar cada categoria. Cores bem distintas entre si, pensadas
 * para reconhecimento rápido enquanto o usuário está dirigindo.
 */
val PALETA_CORES_CATEGORIA = listOf(
    Color.rgb(211, 47, 47),   // vermelho
    Color.rgb(230, 81, 0),    // laranja escuro
    Color.rgb(249, 168, 37),  // amarelo / dourado
    Color.rgb(56, 142, 60),   // verde
    Color.rgb(0, 151, 167),   // ciano
    Color.rgb(21, 101, 192),  // azul
    Color.rgb(94, 53, 177),   // roxo
    Color.rgb(216, 27, 96),   // rosa
    Color.rgb(93, 64, 55),    // marrom
    Color.rgb(97, 97, 97),    // cinza
    Color.rgb(0, 121, 107),   // verde-azulado
    Color.rgb(191, 54, 12)    // terracota
)

val COR_PADRAO_CATEGORIA = Color.rgb(21, 101, 192)

/**
 * Decide se o texto de um botão deve ser branco ou preto de acordo
 * com o brilho da cor de fundo, garantindo contraste legível.
 */
fun corContraste(cor: Int): Int {
    val r = Color.red(cor)
    val g = Color.green(cor)
    val b = Color.blue(cor)
    val luminancia = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (luminancia > 0.6) Color.BLACK else Color.WHITE
}

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val PREFS = "marcador_preferencias"
        private const val KEY_CATEGORIAS = "categorias"
        private const val KEY_PONTOS = "pontos"
        private const val AUTHORITY = "com.example.marcador.fileprovider"
    }

    private lateinit var btnCompartilhar: Button
    private lateinit var btnNovaCategoria: Button
    private lateinit var recyclerCategorias: RecyclerView

    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvPrecisao: TextView
    private lateinit var tvSatelites: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTotal: TextView

    private lateinit var locationManager: LocationManager

    private val categorias = mutableListOf<Categoria>()
    private val pontos = mutableListOf<Ponto>()

    private var adapter: CategoriaAdapter? = null
    private var ultimaLocalizacao: Location? = null

    // ============================
    // ESTADO GNSS (dados reais)
    // ============================
    private val satelites = mutableListOf<SatelliteInfo>()
    private var ultimaAtualizacaoGNSS: Long = 0L
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var gnssDialogAtual: DialogMonitorGNSS? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            if (!deveAceitarLocalizacao(location)) return

            ultimaLocalizacao = Location(location)
            atualizarUI(location)

            tvStatus.text = "GPS OK"
            tvStatus.setTextColor(Color.rgb(46, 125, 50))
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                tvStatus.text = "GPS ativo"
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                tvStatus.text = "GPS desativado"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
         * Mantém a tela sempre ligada enquanto o app estiver em uso,
         * já que o usuário costuma usá-lo dirigindo e não pode ficar
         * destravando o aparelho o tempo todo.
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        inicializarComponentes()
        carregarDadosPersistidos()
        setupRecyclerView()
        setupListeners()
        adicionarBotaoConfiguracoes()

        locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        atualizarTotal()
        verificarPermissoes()
    }

    private fun inicializarComponentes() {
        btnCompartilhar = findViewById(R.id.btnCompartilhar)
        btnNovaCategoria = findViewById(R.id.btnNovaCategoria)
        recyclerCategorias = findViewById(R.id.recyclerCategorias)

        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvPrecisao = findViewById(R.id.tvPrecisao)
        tvSatelites = findViewById(R.id.tvSatelites)
        tvStatus = findViewById(R.id.tvStatus)
        tvTotal = findViewById(R.id.tvTotal)
    }

    /*
     * O botão é criado aqui para não exigir alteração do XML.
     * Assim todo o funcionamento continua concentrado neste MainActivity.kt.
     */
    private fun adicionarBotaoConfiguracoes() {
        val pai = btnCompartilhar.parent as? ViewGroup ?: return

        val botao = Button(this).apply {
            text = "⚙  CONFIGURAÇÕES"
            textSize = 14f
            isAllCaps = false
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                abrirConfiguracoes()
            }
        }

        val indice = pai.indexOfChild(btnCompartilhar)
        pai.addView(
            botao,
            (indice + 1).coerceAtMost(pai.childCount)
        )
    }

    private fun carregarDadosPersistidos() {
        carregarCategorias()
        carregarPontos()
    }

    private fun carregarCategorias() {
        categorias.clear()

        val texto = prefs.getString(KEY_CATEGORIAS, null)

        if (!texto.isNullOrBlank()) {
            try {
                val array = JSONArray(texto)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val nome = obj.optString("nome").trim()
                    val id = obj.optString("id").trim()

                    if (nome.isNotEmpty()) {
                        val cor = if (obj.has("cor")) {
                            obj.optInt("cor", COR_PADRAO_CATEGORIA)
                        } else {
                            PALETA_CORES_CATEGORIA[i % PALETA_CORES_CATEGORIA.size]
                        }

                        categorias.add(
                            Categoria(
                                nome = nome,
                                id = if (id.isNotEmpty()) id else gerarIdCategoria(nome),
                                cor = cor
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                categorias.clear()
            }
        }

        /*
         * Só cria as categorias padrão se nunca houver categorias salvas.
         * Depois disso, elas permanecem até serem excluídas manualmente
         * na tela exclusiva de configurações.
         */
        if (categorias.isEmpty()) {
            categorias.addAll(
                listOf(
                    Categoria("Quebra-mola", "quebra_mola", Color.rgb(230, 81, 0)),
                    Categoria("Posto", "posto", Color.rgb(56, 142, 60)),
                    Categoria("Radar", "radar", Color.rgb(211, 47, 47)),
                    Categoria("Perigo", "perigo", Color.rgb(249, 168, 37))
                )
            )
            salvarCategorias()
        }
    }

    private fun salvarCategorias() {
        val array = JSONArray()

        categorias.forEach {
            array.put(
                JSONObject().apply {
                    put("nome", it.nome)
                    put("id", it.id)
                    put("cor", it.cor)
                }
            )
        }

        prefs.edit()
            .putString(KEY_CATEGORIAS, array.toString())
            .apply()
    }

    private fun carregarPontos() {
        pontos.clear()

        val texto = prefs.getString(KEY_PONTOS, null)
            ?: return

        try {
            val array = JSONArray(texto)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                pontos.add(
                    Ponto(
                        nome = obj.optString("nome"),
                        latitude = obj.optDouble("latitude"),
                        longitude = obj.optDouble("longitude"),
                        data = obj.optString("data"),
                        precisao = obj.optDouble("precisao", 0.0),
                        provedor = obj.optString("provedor"),
                        satelites = obj.optInt("satelites", 0)
                    )
                )
            }
        } catch (_: Exception) {
            pontos.clear()
        }
    }

    private fun salvarPontos() {
        val array = JSONArray()

        pontos.forEach {
            array.put(it.toJson())
        }

        prefs.edit()
            .putString(KEY_PONTOS, array.toString())
            .apply()
    }

    private fun setupRecyclerView() {
        adapter = CategoriaAdapter(categorias) { categoria ->
            salvarPonto(categoria)
        }

        /*
         * Grade de 2 colunas: os botões ficam quadrados/maiores,
         * um do lado do outro, em vez de uma lista vertical.
         */
        recyclerCategorias.layoutManager =
            GridLayoutManager(this, 2)

        recyclerCategorias.adapter = adapter
    }

    private fun setupListeners() {
        btnCompartilhar.setOnClickListener {
            compartilharJSON()
        }

        btnNovaCategoria.setOnClickListener {
            novaCategoria()
        }
    }


    private fun iniciarModoFlutuante() {

        if (!android.provider.Settings.canDrawOverlays(this)) {

            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:")
                )

                startActivity(intent)

            } catch (_: Exception) {

                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    )
                )
            }

            Toast.makeText(
                this,
                "Ative a permissão 'Exibir sobre outros apps' para o Marcador.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val fine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            verificarPermissoes()
            return
        }

        val intent = Intent(
            this,
            MarcadorFloatingService::class.java
        )

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(
            this,
            "Modo flutuante iniciado.",
            Toast.LENGTH_SHORT
        ).show()
    }
    private fun iniciarGPS() {
        if (!temPermissao()) {
            tvStatus.text = "Permissão de localização necessária"
            return
        }

        try {
            val gps =
                locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                )

            if (gps != null && idadeLocalizacaoSegundos(gps) <= 30) {
                ultimaLocalizacao = Location(gps)
                atualizarUI(gps)
                tvStatus.text = "Obtendo GPS..."
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )

            if (locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                )
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            tvStatus.text = "Obtendo GPS..."

            registrarGnssStatusCallback()

        } catch (_: SecurityException) {
            tvStatus.text = "Sem permissão GPS"
        } catch (e: Exception) {
            tvStatus.text = "Erro GPS: ${e.message ?: ""}"
        }
    }

    private fun deveAceitarLocalizacao(location: Location): Boolean {
        if (!location.latitude.isFinite() ||
            !location.longitude.isFinite()
        ) {
            return false
        }

        if (location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) {
            return false
        }

        val atual = ultimaLocalizacao ?: return true

        val atualEhGps =
            atual.provider.equals(
                LocationManager.GPS_PROVIDER,
                ignoreCase = true
            )

        val novaEhNetwork =
            location.provider.equals(
                LocationManager.NETWORK_PROVIDER,
                ignoreCase = true
            )

        if (atualEhGps && novaEhNetwork &&
            idadeLocalizacaoSegundos(atual) <= 10
        ) {
            return false
        }

        return true
    }

    private fun idadeLocalizacaoSegundos(location: Location): Long {
        return try {
            val idadeNanos =
                android.os.SystemClock.elapsedRealtimeNanos() -
                        location.elapsedRealtimeNanos

            if (idadeNanos <= 0) {
                0L
            } else {
                idadeNanos / 1_000_000_000L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun atualizarUI(loc: Location) {
        tvLatitude.text =
            String.format(Locale.US, "%.7f", loc.latitude)

        tvLongitude.text =
            String.format(Locale.US, "%.7f", loc.longitude)

        tvPrecisao.text =
            String.format(Locale.US, "%.1f m", loc.accuracy)

        tvSatelites.text =
            obterQuantidadeSatelites(loc).toString()

        tvStatus.text = "GPS OK"
        tvStatus.setTextColor(Color.rgb(46, 125, 50))
    }

    /*
     * Prioriza a contagem real vinda do GnssStatus (satélites
     * efetivamente usados no cálculo da posição). Se ainda não houver
     * dados do GnssStatus disponíveis, cai no valor reportado pelos
     * "extras" do Location (comportamento original, mantido como
     * fallback). Nunca inventa números.
     */
    private fun obterQuantidadeSatelites(location: Location): Int {
        val usadosReal = satelites.count { it.usedInFix }
        if (usadosReal > 0) {
            return usadosReal
        }

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.N
        ) {
            location.extras?.let {
                if (it.containsKey("satellites")) {
                    return it.getInt("satellites", 0)
                }
            }
        }

        return 0
    }

    private fun salvarPonto(categoria: Categoria) {
        try {
            val location = ultimaLocalizacao

            if (location == null) {
                Toast.makeText(
                    this,
                    "Nenhuma coordenada válida disponível.\nAguarde o GPS atualizar.",
                    Toast.LENGTH_SHORT
                ).show()

                iniciarGPS()
                return
            }

            if (!location.latitude.isFinite() ||
                !location.longitude.isFinite() ||
                location.latitude !in -90.0..90.0 ||
                location.longitude !in -180.0..180.0
            ) {
                Toast.makeText(
                    this,
                    "Coordenada GPS inválida.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            if (!location.accuracy.isFinite() ||
                location.accuracy <= 0f
            ) {
                Toast.makeText(
                    this,
                    "Aguardando precisão GPS válida...",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            if (location.accuracy > 100f) {
                Toast.makeText(
                    this,
                    "Precisão GPS insuficiente: %.1f m"
                        .format(location.accuracy),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val data =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

            val ponto = Ponto(
                nome = categoria.nome,
                latitude = location.latitude,
                longitude = location.longitude,
                data = data,
                precisao = location.accuracy.toDouble(),
                provedor = location.provider ?: "",
                satelites = obterQuantidadeSatelites(location)
            )

            pontos.add(ponto)
            salvarPontos()
            atualizarTotal()

            Toast.makeText(
                this,
                "✓ ${categoria.nome} salvo!\n" +
                        String.format(
                            Locale.US,
                            "%.7f, %.7f",
                            ponto.latitude,
                            ponto.longitude
                        ),
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao salvar: ${e.message ?: "erro desconhecido"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun atualizarTotal() {
        tvTotal.text = "Total: ${pontos.size}"
    }

    /*
     * ============================
     * CONFIGURAÇÕES
     * ============================
     */

    private fun abrirConfiguracoes() {
        val dialog = DialogTelaConfiguracoes(this)
        dialog.show()
    }

    private inner class DialogTelaConfiguracoes(
        context: Context
    ) : Dialog(context) {

        private lateinit var listaPontos: LinearLayout
        private lateinit var listaCategorias: LinearLayout
        private lateinit var tvContador: TextView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            window?.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            construirTela()
        }

        private fun construirTela() {
            val fundo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.WHITE)
            }

            val cabecalho = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titulo = TextView(context).apply {
                text = "Configurações"
                textSize = 25f
                setTextColor(Color.rgb(25, 35, 45))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

            val fechar = Button(context).apply {
                text = "FECHAR"
                isAllCaps = false
                setOnClickListener { dismiss() }
            }

            cabecalho.addView(titulo)
            cabecalho.addView(fechar)

            fundo.addView(cabecalho)

            val subtitulo = TextView(context).apply {
                text = "Gerencie pontos, categorias e o monitor GNSS do aparelho."
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 8, 0, 18)
            }

            fundo.addView(subtitulo)

            tvContador = TextView(context).apply {
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 14)
            }

            fundo.addView(tvContador)

            val acoes = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val btnMonitorGNSS =
                criarBotaoDestaque("🛰️  MONITOR GNSS / SATÉLITES") {
                    abrirMonitorGNSS()
                }

            val btnExportarJSON =
                criarBotao("📄  Exportar JSON") {
                    exportar(FormatoExportacao.JSON)
                }

            val btnExportarKML =
                criarBotao("🌍  Exportar KML") {
                    exportar(FormatoExportacao.KML)
                }

            val btnModoFlutuante =
                criarBotaoDestaque("MODO FLUTUANTE") {
                    iniciarModoFlutuante()
                }

            val btnLimparTodos =
                criarBotao("🗑  LIMPAR TODOS OS PONTOS") {
                    confirmarLimparTodos()
                }

            acoes.addView(btnMonitorGNSS)
            acoes.addView(btnExportarJSON)
            acoes.addView(btnExportarKML)
            acoes.addView(btnModoFlutuante)
            acoes.addView(btnLimparTodos)

            fundo.addView(acoes)

            val tituloPontos = criarTituloSecao("PONTOS SALVOS")
            fundo.addView(tituloPontos)

            val scrollPontos = ScrollView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.2f
                    )
            }

            listaPontos = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            scrollPontos.addView(listaPontos)
            fundo.addView(scrollPontos)

            val tituloCategorias =
                criarTituloSecao("CATEGORIAS")

            fundo.addView(tituloCategorias)

            val subtituloCategorias = TextView(context).apply {
                text = "Toque no quadrado colorido para escolher a cor do botão."
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, 8)
            }

            fundo.addView(subtituloCategorias)

            val btnAdicionar =
                criarBotao("＋  Nova categoria") {
                    dialogNovaCategoria()
                }

            fundo.addView(btnAdicionar)

            val scrollCategorias =
                ScrollView(context).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            0,
                            0.8f
                        )
                }

            listaCategorias =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

            scrollCategorias.addView(listaCategorias)
            fundo.addView(scrollCategorias)

            setContentView(fundo)

            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            atualizarTela()
        }

        private fun criarTituloSecao(texto: String): TextView {
            return TextView(context).apply {
                text = texto
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(80, 90, 100))
                setPadding(0, 18, 0, 8)
            }
        }

        private fun criarBotao(
            texto: String,
            acao: () -> Unit
        ): Button {
            return Button(context).apply {
                text = texto
                isAllCaps = false
                textSize = 15f
                setPadding(18, 12, 18, 12)
                setOnClickListener { acao() }

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
            }
        }

        /**
         * Botão de maior destaque visual, usado para a entrada do
         * Monitor GNSS (fundo colorido, texto branco e maior).
         */
        private fun criarBotaoDestaque(
            texto: String,
            acao: () -> Unit
        ): Button {
            return Button(context).apply {
                text = texto
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(21, 101, 192))
                setPadding(20, 26, 20, 26)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener { acao() }

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 14
                    }
            }
        }

        private fun atualizarTela() {
            tvContador.text =
                "📍 ${pontos.size} ponto(s) salvo(s)"

            listaPontos.removeAllViews()

            if (pontos.isEmpty()) {
                listaPontos.addView(
                    TextView(context).apply {
                        text = "Nenhum ponto salvo."
                        textSize = 15f
                        setTextColor(Color.GRAY)
                        setPadding(8, 18, 8, 18)
                    }
                )
            } else {
                pontos.forEachIndexed { index, ponto ->
                    val linha =
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(8, 10, 8, 10)
                            isClickable = true
                            isLongClickable = true
                            isHapticFeedbackEnabled = true
                            setOnLongClickListener { view ->
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS
                                )
                                compartilharPermalinkWaze(ponto)
                                true
                            }
                        }

                    val info =
                        TextView(context).apply {
                            text =
                                "${ponto.nome}\n" +
                                        String.format(
                                            Locale.US,
                                            "%.7f, %.7f",
                                            ponto.latitude,
                                            ponto.longitude
                                        ) +
                                        "\n${ponto.data}  •  ±${ponto.precisao.toInt()}m" +
                                        "  •  sat: ${ponto.satelites}" +
                                        (if (ponto.provedor.isNotBlank()) "  •  ${ponto.provedor}" else "") +
                                        "\n🔗 segure para compartilhar no Waze"

                            textSize = 14f
                            setTextColor(Color.rgb(40, 45, 50))

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                        }

                    val excluir =
                        Button(context).apply {
                            text = "Excluir"
                            isAllCaps = false
                            setOnClickListener {
                                confirmarExcluirPonto(index)
                            }
                        }

                    linha.addView(info)
                    linha.addView(excluir)

                    listaPontos.addView(linha)

                    listaPontos.addView(
                        View(context).apply {
                            setBackgroundColor(
                                Color.rgb(225, 225, 225)
                            )
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1
                                )
                        }
                    )
                }
            }

            listaCategorias.removeAllViews()

            categorias.forEachIndexed { index, categoria ->
                val linha =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(8, 6, 8, 6)
                    }

                val tamanhoSwatch =
                    (36 * context.resources.displayMetrics.density).toInt()

                val corView =
                    View(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                tamanhoSwatch,
                                tamanhoSwatch
                            ).apply {
                                marginEnd = 20
                            }
                        setBackgroundColor(categoria.cor)
                        isClickable = true
                        setOnClickListener {
                            abrirSeletorCor(index)
                        }
                    }

                val nome =
                    TextView(context).apply {
                        text = categoria.nome
                        textSize = 16f
                        setTextColor(Color.rgb(35, 40, 45))
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                    }

                val excluir =
                    Button(context).apply {
                        text = "Excluir"
                        isAllCaps = false
                        setOnClickListener {
                            confirmarExcluirCategoria(index)
                        }
                    }

                linha.addView(corView)
                linha.addView(nome)
                linha.addView(excluir)

                listaCategorias.addView(linha)
            }
        }

        /**
         * Abre um seletor de cor em grade para a categoria informada.
         * Ao tocar em uma cor, ela é aplicada e salva imediatamente,
         * e o botão correspondente na tela principal é atualizado.
         */
        private fun abrirSeletorCor(index: Int) {
            if (index !in categorias.indices) return

            val categoria = categorias[index]

            lateinit var dialogSelecao: AlertDialog

            val grid = GridLayout(context).apply {
                columnCount = 4
                setPadding(24, 24, 24, 24)
            }

            val tamanho =
                (56 * context.resources.displayMetrics.density).toInt()

            PALETA_CORES_CATEGORIA.forEach { cor ->
                val quadrado = View(context).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = tamanho
                        height = tamanho
                        setMargins(12, 12, 12, 12)
                    }

                    if (cor == categoria.cor) {
                        background = android.graphics.drawable.LayerDrawable(
                            arrayOf(
                                android.graphics.drawable.ColorDrawable(Color.BLACK),
                                android.graphics.drawable.ColorDrawable(cor)
                            )
                        ).apply {
                            setLayerInset(1, 6, 6, 6, 6)
                        }
                    } else {
                        setBackgroundColor(cor)
                    }

                    setOnClickListener {
                        categoria.cor = cor
                        salvarCategorias()
                        adapter?.notifyDataSetChanged()
                        atualizarTela()
                        dialogSelecao.dismiss()
                    }
                }

                grid.addView(quadrado)
            }

            dialogSelecao = AlertDialog.Builder(context)
                .setTitle("Cor de \"${categoria.nome}\"")
                .setView(grid)
                .setNegativeButton("Fechar", null)
                .create()

            dialogSelecao.show()
        }

        private fun confirmarExcluirPonto(index: Int) {
            AlertDialog.Builder(context)
                .setTitle("Excluir ponto?")
                .setMessage(
                    "O ponto será removido permanentemente."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ ->
                    if (index in pontos.indices) {
                        pontos.removeAt(index)
                        salvarPontos()
                        atualizarTotal()
                        atualizarTela()
                    }
                }
                .show()
        }

        private fun confirmarLimparTodos() {
            if (pontos.isEmpty()) {
                Toast.makeText(
                    context,
                    "Não há pontos para limpar.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            AlertDialog.Builder(context)
                .setTitle("Limpar todos os pontos?")
                .setMessage(
                    "Todos os ${pontos.size} pontos serão apagados. " +
                            "As categorias permanecerão salvas."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar") { _, _ ->
                    pontos.clear()
                    salvarPontos()
                    atualizarTotal()
                    atualizarTela()

                    Toast.makeText(
                        context,
                        "Todos os pontos foram apagados.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        }

        private fun confirmarExcluirCategoria(index: Int) {
            if (index !in categorias.indices) return

            val categoria = categorias[index]

            AlertDialog.Builder(context)
                .setTitle("Excluir categoria?")
                .setMessage(
                    "A categoria \"${categoria.nome}\" será removida " +
                            "e não voltará após reiniciar o aplicativo."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ ->
                    categorias.removeAt(index)
                    salvarCategorias()
                    adapter?.notifyDataSetChanged()
                    atualizarTela()

                    Toast.makeText(
                        context,
                        "Categoria excluída.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        }

        private fun dialogNovaCategoria() {
            val campo = EditText(context).apply {
                hint = "Ex.: Escola, Ponte, Buraco..."
                setSingleLine(true)
                setPadding(25, 20, 25, 20)
            }

            AlertDialog.Builder(context)
                .setTitle("Nova categoria")
                .setView(campo)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar") { _, _ ->
                    val nome =
                        campo.text.toString().trim()

                    if (nome.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Informe um nome.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    val existe =
                        categorias.any {
                            it.nome.equals(
                                nome,
                                ignoreCase = true
                            )
                        }

                    if (existe) {
                        Toast.makeText(
                            context,
                            "Essa categoria já existe.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    categorias.add(
                        Categoria(
                            nome = nome,
                            id = gerarIdCategoria(nome),
                            cor = PALETA_CORES_CATEGORIA[
                                categorias.size % PALETA_CORES_CATEGORIA.size
                            ]
                        )
                    )

                    salvarCategorias()
                    adapter?.notifyDataSetChanged()
                    atualizarTela()

                    Toast.makeText(
                        context,
                        "Categoria criada e salva.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        }
    }

    private fun novaCategoria() {
        val campo = EditText(this).apply {
            hint = "Nome da categoria"
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Nova categoria")
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar") { _, _ ->
                val nome =
                    campo.text.toString().trim()

                if (nome.isEmpty()) return@setPositiveButton

                if (categorias.any {
                        it.nome.equals(
                            nome,
                            ignoreCase = true
                        )
                    }
                ) {
                    Toast.makeText(
                        this,
                        "Essa categoria já existe.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                categorias.add(
                    Categoria(
                        nome = nome,
                        id = gerarIdCategoria(nome),
                        cor = PALETA_CORES_CATEGORIA[
                            categorias.size % PALETA_CORES_CATEGORIA.size
                        ]
                    )
                )

                salvarCategorias()
                adapter?.notifyDataSetChanged()

                Toast.makeText(
                    this,
                    "Categoria criada e salva. Você pode mudar a cor " +
                            "dela em Configurações.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun gerarIdCategoria(nome: String): String {
        return nome
            .lowercase(Locale.getDefault())
            .normalizeSemAcento()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "categoria_${System.currentTimeMillis()}" }
    }

    private fun String.normalizeSemAcento(): String {
        return java.text.Normalizer
            .normalize(
                this,
                java.text.Normalizer.Form.NFD
            )
            .replace(
                Regex("\\p{InCombiningDiacriticalMarks}+"),
                ""
            )
    }

    /*
     * ============================
     * EXPORTAÇÃO
     * ============================
     * A estrutura abaixo já está preparada para novos formatos
     * (CSV, GPX, GeoJSON) no futuro, bastando implementar o gerador
     * de arquivo correspondente e tratar o novo caso no "when".
     */

    private enum class FormatoExportacao {
        JSON, KML, CSV, GPX, GEOJSON
    }

    private fun exportar(formato: FormatoExportacao) {
        when (formato) {
            FormatoExportacao.JSON -> exportarJSON()
            FormatoExportacao.KML -> exportarKML()
            FormatoExportacao.CSV,
            FormatoExportacao.GPX,
            FormatoExportacao.GEOJSON -> {
                Toast.makeText(
                    this,
                    "Este formato de exportação ainda não foi implementado.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun compartilharJSON() {
        if (pontos.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhuma coordenada salva.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        compartilharArquivo(
            criarArquivoJSON(),
            "application/json",
            "Compartilhar JSON"
        )
    }

    private fun exportarJSON() {
        if (pontos.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhuma coordenada salva.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        compartilharArquivo(
            criarArquivoJSON(),
            "application/json",
            "Exportar JSON"
        )
    }

    private fun criarArquivoJSON(): File {
        val arquivo =
            File(
                cacheDir,
                "pontos_${System.currentTimeMillis()}.json"
            )

        val raiz = JSONObject()
        raiz.put("status", "success")
        raiz.put("total", pontos.size)

        val array = JSONArray()
        pontos.forEach {
            array.put(it.toJson())
        }

        raiz.put("pontos", array)

        arquivo.writeText(
            raiz.toString(2),
            Charsets.UTF_8
        )

        return arquivo
    }

    /*
     * ============================
     * EXPORTAÇÃO KML
     * ============================
     */

    private fun exportarKML() {
        if (pontos.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhuma coordenada salva.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val arquivo =
                File(
                    cacheDir,
                    "pontos_${System.currentTimeMillis()}.kml"
                )

            val xml =
                buildString {
                    append(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    )
                    append(
                        "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n"
                    )
                    append("  <Document>\n")
                    append("    <name>MarcadorCoordenadas</name>\n")

                    pontos.forEach { ponto ->
                        append("    <Placemark>\n")
                        append(
                            "      <name>${escapeXml(ponto.nome)}</name>\n"
                        )
                        append("      <description><![CDATA[\n")
                        append(
                            "Coordenadas: ${formatarCoordenadas(ponto.latitude, ponto.longitude)}<br/>" +
                                    "Data: ${ponto.data}<br/>" +
                                    "Precisão: ${ponto.precisao} m<br/>" +
                                    "Satélites: ${ponto.satelites}<br/>" +
                                    "Provedor: ${escapeXml(ponto.provedor)}<br/>" +
                                    "Permalink (Waze): ${gerarLinkWaze(ponto.latitude, ponto.longitude)}"
                        )
                        append("]]></description>\n")
                        append("      <Point>\n")
                        append(
                            String.format(
                                Locale.US,
                                "        <coordinates>%.7f,%.7f,0</coordinates>\n",
                                ponto.longitude,
                                ponto.latitude
                            )
                        )
                        append("      </Point>\n")
                        append("    </Placemark>\n")
                    }

                    append("  </Document>\n")
                    append("</kml>\n")
                }

            arquivo.writeText(
                xml,
                Charsets.UTF_8
            )

            compartilharArquivo(
                arquivo,
                "application/vnd.google-earth.kml+xml",
                "Exportar KML"
            )

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao criar KML: ${e.message ?: ""}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun escapeXml(valor: String): String {
        return valor
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun compartilharArquivo(
        arquivo: File,
        mimeType: String,
        titulo: String
    ) {
        try {
            val uri: Uri =
                FileProvider.getUriForFile(
                    this,
                    AUTHORITY,
                    arquivo
                )

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

            startActivity(
                Intent.createChooser(
                    intent,
                    titulo
                )
            )

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao compartilhar: ${e.message ?: ""}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Compartilha um texto simples (ex.: um link) através do sistema
     * de compartilhamento do Android (WhatsApp, e-mail, etc.).
     */
    private fun compartilharTexto(texto: String, titulo: String) {
        try {
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, texto)
                }

            startActivity(
                Intent.createChooser(
                    intent,
                    titulo
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao compartilhar link: ${e.message ?: ""}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Gera e compartilha o permalink do Waze para o ponto informado,
     * usando as coordenadas reais salvas para aquele ponto.
     */
    private fun compartilharPermalinkWaze(ponto: Ponto) {
        val link = gerarLinkWaze(ponto.latitude, ponto.longitude)

        compartilharTexto(
            "${ponto.nome}\n$link",
            "Compartilhar no Waze"
        )
    }

    /*
     * ============================
     * MONITOR GNSS / SATÉLITES
     * ============================
     */

    private fun abrirMonitorGNSS() {
        val dialog = DialogMonitorGNSS(this)
        gnssDialogAtual = dialog
        dialog.setOnDismissListener {
            if (gnssDialogAtual === dialog) {
                gnssDialogAtual = null
            }
        }
        dialog.show()
    }

    /**
     * Registra o callback oficial do Android (GnssStatus.Callback)
     * para receber, em tempo real, os satélites GNSS realmente
     * detectados pelo aparelho. Não gera nem simula nenhum dado.
     */
    private fun registrarGnssStatusCallback() {
        if (!temPermissao()) return
        if (gnssStatusCallback != null) return

        try {
            val callback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    processarGnssStatus(status)
                }

                override fun onStopped() {
                    satelites.clear()
                    gnssDialogAtual?.atualizarConteudo()
                }
            }

            val registrado = locationManager.registerGnssStatusCallback(
                callback,
                Handler(Looper.getMainLooper())
            )

            gnssStatusCallback = if (registrado) callback else null

        } catch (_: SecurityException) {
            gnssStatusCallback = null
        } catch (_: Exception) {
            gnssStatusCallback = null
        }
    }

    private fun pararGnssStatusCallback() {
        val callback = gnssStatusCallback ?: return

        try {
            locationManager.unregisterGnssStatusCallback(callback)
        } catch (_: Exception) {
        }

        gnssStatusCallback = null
    }

    /**
     * Converte o GnssStatus reportado pelo Android em uma lista de
     * SatelliteInfo. Todos os valores vêm diretamente da API; quando
     * uma informação não está disponível no aparelho, o campo fica
     * nulo e a interface mostra "Não disponível".
     */
    private fun processarGnssStatus(status: GnssStatus) {
        val lista = mutableListOf<SatelliteInfo>()
        val agora = System.currentTimeMillis()

        for (i in 0 until status.satelliteCount) {
            try {
                val tipo = status.getConstellationType(i)

                val carrierFreq =
                    if (android.os.Build.VERSION.SDK_INT >= 26 &&
                        status.hasCarrierFrequencyHz(i)
                    ) {
                        status.getCarrierFrequencyHz(i)
                    } else {
                        null
                    }

                val basebandCn0 =
                    if (android.os.Build.VERSION.SDK_INT >= 30 &&
                        status.hasBasebandCn0DbHz(i)
                    ) {
                        status.getBasebandCn0DbHz(i)
                    } else {
                        null
                    }

                lista.add(
                    SatelliteInfo(
                        constellationType = tipo,
                        constellationName = nomeConstelacao(tipo),
                        svid = status.getSvid(i),
                        azimuth = status.getAzimuthDegrees(i),
                        elevation = status.getElevationDegrees(i),
                        cn0 = status.getCn0DbHz(i),
                        usedInFix = status.usedInFix(i),
                        hasEphemeris = status.hasEphemerisData(i),
                        hasAlmanac = status.hasAlmanacData(i),
                        carrierFrequencyHz = carrierFreq,
                        basebandCn0DbHz = basebandCn0,
                        timestamp = agora
                    )
                )
            } catch (_: Exception) {
                // Se um índice individual falhar, ignora apenas ele
                // e continua processando os demais satélites.
            }
        }

        satelites.clear()
        satelites.addAll(lista)
        ultimaAtualizacaoGNSS = agora

        gnssDialogAtual?.atualizarConteudo()
    }

    private fun nomeConstelacao(tipo: Int): String {
        return when (tipo) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
            GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
            GnssStatus.CONSTELLATION_IRNSS -> "NAVIC"
            else -> "DESCONHECIDA"
        }
    }

    private fun rotuloSatelite(sat: SatelliteInfo): String {
        return if (sat.constellationType == GnssStatus.CONSTELLATION_GPS) {
            "PRN ${sat.svid}"
        } else {
            "ID ${sat.svid}"
        }
    }

    private fun corParaSinal(cn0: Float): Int {
        return when {
            cn0 >= 35f -> Color.rgb(46, 125, 50)   // sinal forte
            cn0 >= 20f -> Color.rgb(255, 152, 0)   // sinal médio
            else -> Color.rgb(158, 158, 158)       // sinal fraco
        }
    }

    /**
     * Tela interna (Dialog) do Monitor GNSS. Continua dentro do
     * MainActivity, sem nova Activity, conforme exigido.
     */
    private inner class DialogMonitorGNSS(
        context: Context
    ) : Dialog(context) {

        private lateinit var tvResumo: TextView
        private lateinit var tvConstelacoes: TextView
        private lateinit var tvAtualizado: TextView
        private lateinit var skyView: SkyView
        private lateinit var listaSatelites: LinearLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            window?.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            construirTela()
        }

        private fun construirTela() {
            val fundo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(248, 249, 251))
            }

            val cabecalho = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.rgb(13, 71, 161))
                setPadding(24, 28, 24, 28)
            }

            val titulo = TextView(context).apply {
                text = "🛰️  MONITOR GNSS"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

            val fechar = Button(context).apply {
                text = "FECHAR"
                isAllCaps = false
                setTextColor(Color.rgb(13, 71, 161))
                setBackgroundColor(Color.WHITE)
                setOnClickListener { dismiss() }
            }

            cabecalho.addView(titulo)
            cabecalho.addView(fechar)
            fundo.addView(cabecalho)

            val scroll = ScrollView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
            }

            val coluna = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 40)
            }

            // Card de resumo
            val cardResumo = criarCard()

            tvResumo = TextView(context).apply {
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(25, 35, 45))
            }

            tvConstelacoes = TextView(context).apply {
                textSize = 14f
                setTextColor(Color.rgb(70, 80, 90))
                setPadding(0, 8, 0, 0)
            }

            tvAtualizado = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 10, 0, 0)
            }

            cardResumo.addView(tvResumo)
            cardResumo.addView(tvConstelacoes)
            cardResumo.addView(tvAtualizado)
            coluna.addView(cardResumo)

            // Card do Sky View
            val cardSky = criarCard()

            val tituloSky = TextView(context).apply {
                text = "SKY VIEW"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(80, 90, 100))
                setPadding(0, 0, 0, 12)
            }

            skyView = SkyView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpParaPx(320)
                )
                onSatelliteClick = { sat -> mostrarDetalhesSatelite(sat) }
            }

            val legenda = TextView(context).apply {
                text = "● usado no cálculo   ○ visível, não usado   " +
                        "cor: verde=forte, laranja=médio, cinza=fraco"
                textSize = 11f
                setTextColor(Color.GRAY)
                setPadding(0, 12, 0, 0)
            }

            cardSky.addView(tituloSky)
            cardSky.addView(skyView)
            cardSky.addView(legenda)
            coluna.addView(cardSky)

            // Lista de satélites
            val tituloLista = TextView(context).apply {
                text = "SATÉLITES DETECTADOS"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(80, 90, 100))
                setPadding(4, 24, 4, 10)
            }

            coluna.addView(tituloLista)

            listaSatelites = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            coluna.addView(listaSatelites)

            scroll.addView(coluna)
            fundo.addView(scroll)

            setContentView(fundo)

            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            atualizarConteudo()
        }

        private fun criarCard(): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(24, 22, 24, 22)

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 18
                    }
            }
        }

        private fun dpParaPx(dp: Int): Int {
            val densidade = context.resources.displayMetrics.density
            return (dp * densidade).toInt()
        }

        fun atualizarConteudo() {
            if (!isShowing) return

            val usados = satelites.count { it.usedInFix }

            tvResumo.text =
                "Satélites visíveis: ${satelites.size}   •   " +
                        "Usados no cálculo: $usados"

            val porConstelacao = satelites
                .groupBy { it.constellationName }
                .toSortedMap()

            tvConstelacoes.text =
                if (porConstelacao.isEmpty()) {
                    "Nenhuma constelação detectada ainda."
                } else {
                    porConstelacao.entries.joinToString("     ") {
                        "${it.key}: ${it.value.size}"
                    }
                }

            tvAtualizado.text =
                if (ultimaAtualizacaoGNSS > 0) {
                    "Status: " +
                            (if (temPermissao()) "GNSS ativo" else "Sem permissão") +
                            "   •   Última atualização: " +
                            SimpleDateFormat(
                                "HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(ultimaAtualizacaoGNSS))
                } else {
                    "Aguardando dados do GNSS..."
                }

            skyView.satelites = satelites.toList()

            listaSatelites.removeAllViews()

            if (satelites.isEmpty()) {
                listaSatelites.addView(
                    TextView(context).apply {
                        text = "Nenhum satélite detectado no momento."
                        textSize = 14f
                        setTextColor(Color.GRAY)
                        setPadding(8, 12, 8, 12)
                    }
                )
                return
            }

            satelites
                .sortedWith(
                    compareByDescending<SatelliteInfo> { it.usedInFix }
                        .thenByDescending { it.cn0 }
                )
                .forEach { sat ->
                    listaSatelites.addView(criarItemSatelite(sat))
                }
        }

        private fun criarItemSatelite(sat: SatelliteInfo): View {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(20, 16, 20, 16)

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 10
                    }

                setOnClickListener { mostrarDetalhesSatelite(sat) }
            }

            val linhaTitulo = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titulo = TextView(context).apply {
                text = "${sat.constellationName} • ${rotuloSatelite(sat)}"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(25, 35, 45))
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

            val badge = TextView(context).apply {
                text = if (sat.usedInFix) "USADO" else "VISÍVEL"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    if (sat.usedInFix) Color.rgb(46, 125, 50)
                    else Color.rgb(158, 158, 158)
                )
                setPadding(14, 6, 14, 6)
            }

            linhaTitulo.addView(titulo)
            linhaTitulo.addView(badge)
            card.addView(linhaTitulo)

            val detalhes = TextView(context).apply {
                text =
                    "Elevação: ${"%.1f".format(sat.elevation)}°   " +
                            "Azimute: ${"%.1f".format(sat.azimuth)}°   " +
                            "C/N₀: ${"%.1f".format(sat.cn0)} dB-Hz"
                textSize = 13f
                setTextColor(Color.rgb(90, 100, 110))
                setPadding(0, 8, 0, 0)
            }

            card.addView(detalhes)

            return card
        }

        fun mostrarDetalhesSatelite(sat: SatelliteInfo) {
            val texto = buildString {
                append("Constelação: ${sat.constellationName}\n")
                append("SVID: ${sat.svid}\n")
                append("Identificador: ${rotuloSatelite(sat)}\n\n")
                append("Elevação: ${"%.1f".format(sat.elevation)}°\n")
                append("Azimute: ${"%.1f".format(sat.azimuth)}°\n")
                append("C/N₀: ${"%.1f".format(sat.cn0)} dB-Hz\n\n")
                append("Usado no cálculo: ${if (sat.usedInFix) "SIM" else "NÃO"}\n")
                append(
                    "Efemérides: " +
                            (if (sat.hasEphemeris) "Disponível" else "Não disponível") +
                            "\n"
                )
                append(
                    "Almanaque: " +
                            (if (sat.hasAlmanac) "Disponível" else "Não disponível") +
                            "\n\n"
                )
                append(
                    "Frequência da portadora: " +
                            (sat.carrierFrequencyHz?.let {
                                "%.0f Hz".format(it)
                            } ?: "Não disponível") +
                            "\n"
                )
                append(
                    "C/N₀ baseband: " +
                            (sat.basebandCn0DbHz?.let {
                                "%.1f dB-Hz".format(it)
                            } ?: "Não disponível") +
                            "\n\n"
                )
                append(
                    "Horário da leitura: " +
                            SimpleDateFormat(
                                "HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(sat.timestamp))
                )
            }

            AlertDialog.Builder(context)
                .setTitle("${sat.constellationName} • ${rotuloSatelite(sat)}")
                .setMessage(texto)
                .setPositiveButton("Fechar", null)
                .show()
        }
    }

    /**
     * View customizada que desenha o "radar do céu" (Sky View):
     * centro = zênite, borda = horizonte. A posição de cada satélite
     * é calculada a partir de azimute e elevação reais.
     */
    private inner class SkyView(context: Context) : View(context) {

        var satelites: List<SatelliteInfo> = emptyList()
            set(value) {
                field = value
                invalidate()
            }

        var onSatelliteClick: ((SatelliteInfo) -> Unit)? = null

        private var pontosTela: List<PontoTela> = emptyList()

        private val paintCirculo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(210, 215, 220)
            strokeWidth = 2f
        }

        private val paintEixo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(225, 228, 232)
            strokeWidth = 1.5f
        }

        private val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 100, 110)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        private val paintSatUsado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val paintSatVisivel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val raio = (minOf(w, h) / 2f) - 70f

            if (raio <= 0f) return

            // Eixos N-S e L-O
            canvas.drawLine(cx, cy - raio, cx, cy + raio, paintEixo)
            canvas.drawLine(cx - raio, cy, cx + raio, cy, paintEixo)

            // Círculos de referência de elevação (0°, 30°, 60°, 90°)
            listOf(0, 30, 60, 90).forEach { elevacao ->
                val r = raio * (1f - elevacao / 90f)
                canvas.drawCircle(cx, cy, r, paintCirculo)
            }

            canvas.drawText("N", cx, cy - raio - 24f, paintTexto)
            canvas.drawText("S", cx, cy + raio + 46f, paintTexto)
            canvas.drawText("L", cx + raio + 34f, cy + 12f, paintTexto)
            canvas.drawText("O", cx - raio - 34f, cy + 12f, paintTexto)

            val novosPontos = mutableListOf<PontoTela>()

            satelites.forEach { sat ->
                val elevacaoClamp = sat.elevation.coerceIn(0f, 90f)
                val r = raio * (1f - elevacaoClamp / 90f)
                val azRad = Math.toRadians(sat.azimuth.toDouble())

                val x = cx + r * sin(azRad).toFloat()
                val y = cy - r * cos(azRad).toFloat()

                val cor = corParaSinal(sat.cn0)

                if (sat.usedInFix) {
                    paintSatUsado.color = cor
                    canvas.drawCircle(x, y, 18f, paintSatUsado)
                    canvas.drawCircle(x, y, 18f, paintEixo)
                } else {
                    paintSatVisivel.color = cor
                    canvas.drawCircle(x, y, 13f, paintSatVisivel)
                }

                novosPontos.add(PontoTela(sat, x, y))
            }

            pontosTela = novosPontos
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                val toqueX = event.x
                val toqueY = event.y

                val maisProximo = pontosTela.minByOrNull {
                    hypot((it.x - toqueX).toDouble(), (it.y - toqueY).toDouble())
                }

                if (maisProximo != null) {
                    val distancia = hypot(
                        (maisProximo.x - toqueX).toDouble(),
                        (maisProximo.y - toqueY).toDouble()
                    )

                    if (distancia <= 45.0) {
                        onSatelliteClick?.invoke(maisProximo.satelite)
                        performClick()
                        return true
                    }
                }
            }

            return true
        }

        override fun performClick(): Boolean {
            return super.performClick()
        }
    }

    /*
     * ============================
     * PERMISSÕES / CICLO DE VIDA
     * ============================
     */

    private fun temPermissao(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun verificarPermissoes() {
        if (!temPermissao()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            iniciarGPS()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (
                grantResults.any {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {
                iniciarGPS()
            } else {
                tvStatus.text = "Permissão GPS negada"
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::locationManager.isInitialized &&
            temPermissao()
        ) {
            iniciarGPS()
        }
    }

    override fun onPause() {
        super.onPause()

        if (::locationManager.isInitialized) {
            try {
                locationManager.removeUpdates(
                    locationListener
                )
            } catch (_: Exception) {
            }
        }

        pararGnssStatusCallback()
    }
}

/*
 * ============================
 * MODELOS
 * ============================
 */

data class Categoria(
    val nome: String,
    val id: String,
    var cor: Int = COR_PADRAO_CATEGORIA
)

data class Ponto(
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val data: String,
    val precisao: Double,
    val provedor: String,
    val satelites: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("nome", nome)
            put("latitude", latitude)
            put("longitude", longitude)
            put("coordenadas", formatarCoordenadas(latitude, longitude))
            put("permalink", gerarLinkWaze(latitude, longitude))
            put("data", data)
            put("precisao", precisao)
            put("provedor", provedor)
            put("satelites", satelites)
        }
    }
}

/**
 * Formata latitude/longitude no padrão "-9.50982, -35.82036"
 * (5 casas decimais, separadas por vírgula e espaço).
 */
fun formatarCoordenadas(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

/**
 * Gera o link permanente (permalink) do Waze Map Editor para o ponto,
 * no mesmo padrão usado pelo Waze:
 * https://www.waze.com/pt-BR/editor?env=row&lat=...&lon=...&marker=true&zoomLevel=20
 */
fun gerarLinkWaze(latitude: Double, longitude: Double): String {
    val lat = String.format(Locale.US, "%.5f", latitude)
    val lon = String.format(Locale.US, "%.5f", longitude)
    return "https://www.waze.com/pt-BR/editor?env=row&lat=$lat&lon=$lon&marker=true&zoomLevel=20"
}

/**
 * Representa um único satélite reportado pela API GnssStatus do
 * Android em um instante. Todos os valores vêm diretamente do
 * sistema operacional; nenhum campo é inventado ou simulado.
 * Campos que a API não disponibiliza no aparelho ficam nulos.
 */
data class SatelliteInfo(
    val constellationType: Int,
    val constellationName: String,
    val svid: Int,
    val azimuth: Float,
    val elevation: Float,
    val cn0: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val carrierFrequencyHz: Float?,
    val basebandCn0DbHz: Float?,
    val timestamp: Long
)

/**
 * Ponto já convertido para coordenadas de tela (x, y) dentro do
 * Sky View, usado para detectar toques sobre um satélite.
 */
private data class PontoTela(
    val satelite: SatelliteInfo,
    val x: Float,
    val y: Float
)

/*
 * ============================
 * ADAPTER
 * ============================
 */

class CategoriaAdapter(
    private val categorias: List<Categoria>,
    private val onClick: (Categoria) -> Unit
) : RecyclerView.Adapter<CategoriaAdapter.ViewHolder>() {

    class ViewHolder(
        val button: Button
    ) : RecyclerView.ViewHolder(button)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val contexto = parent.context
        val densidade = contexto.resources.displayMetrics.density

        // Botões quadrados/maiores, dispostos em grade de 2 colunas
        // pelo GridLayoutManager configurado na Activity.
        val alturaPx = (150 * densidade).toInt()
        val margemPx = (8 * densidade).toInt()

        val button =
            Button(contexto).apply {
                layoutParams =
                    ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        alturaPx
                    ).apply {
                        setMargins(margemPx, margemPx, margemPx, margemPx)
                    }

                gravity = Gravity.CENTER
                textSize = 17f
                isAllCaps = false
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(16, 16, 16, 16)
            }

        return ViewHolder(button)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val categoria = categorias[position]

        holder.button.text = "📍\n${categoria.nome}"
        holder.button.setBackgroundColor(categoria.cor)
        holder.button.setTextColor(corContraste(categoria.cor))

        holder.button.setOnClickListener {
            onClick(categoria)
        }
    }

    override fun getItemCount(): Int {
        return categorias.size
    }
}
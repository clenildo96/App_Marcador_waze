package com.example.marcador

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MarcadorFloatingService : Service() {

    companion object {

        private const val CHANNEL_ID =
            "marcador_modo_flutuante"

        private const val NOTIFICATION_ID =
            4401

        private const val PREFS =
            "marcador_preferencias"

        private const val KEY_X =
            "floating_x"

        private const val KEY_Y =
            "floating_y"

        private const val KEY_EXPANDED =
            "floating_expanded"
    }

    private lateinit var windowManager: WindowManager

    private lateinit var locationManager: LocationManager

    private var janela: View? = null

    private var parametros: WindowManager.LayoutParams? = null

    private var ultimaLocalizacao: Location? = null

    private var expandido = true

    private val locationListener =
        object : LocationListener {

            override fun onLocationChanged(
                location: Location
            ) {

                if (!location.latitude.isFinite() ||
                    !location.longitude.isFinite()
                ) {
                    return
                }

                ultimaLocalizacao =
                    Location(location)
            }

            override fun onProviderEnabled(
                provider: String
            ) {
            }

            override fun onProviderDisabled(
                provider: String
            ) {
            }
        }

    override fun onCreate() {

        super.onCreate()

        windowManager =
            getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        locationManager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        criarCanalNotificacao()

        iniciarComoForeground()

        iniciarLocalizacao()

        criarJanela()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (janela == null) {
            criarJanela()
        }

        return START_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    private fun iniciarComoForeground() {

        val notification =
            criarNotificacao()

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun criarCanalNotificacao() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val canal =
                NotificationChannel(
                    CHANNEL_ID,
                    "Marcador flutuante",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Modo flutuante do Marcador"

                    setShowBadge(false)
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                canal
            )
        }
    }

    private fun criarNotificacao(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.mipmap.ic_launcher
            )
            .setContentTitle(
                "Marcador flutuante ativo"
            )
            .setContentText(
                "Os botoes do Marcador estao sobre a navegacao"
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun iniciarLocalizacao() {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            return
        }

        try {

            val gps =
                locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                )

            if (gps != null) {
                ultimaLocalizacao =
                    Location(gps)
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0f,
                locationListener,
                android.os.Looper.getMainLooper()
            )

            if (
                locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                )
            ) {

                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    android.os.Looper.getMainLooper()
                )
            }

        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun criarJanela() {

        if (!Settings.canDrawOverlays(this)) {

            stopSelf()
            return
        }

        val prefs =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            )

        expandido =
            prefs.getBoolean(
                KEY_EXPANDED,
                true
            )

        val painel =
            criarPainel()

        val tipoJanela =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val largura =
            dp(
                if (expandido) 190 else 62
            )

        val altura =
            WindowManager.LayoutParams.WRAP_CONTENT

        val xSalvo =
            prefs.getInt(
                KEY_X,
                0
            )

        val ySalvo =
            prefs.getInt(
                KEY_Y,
                180
            )

        parametros =
            WindowManager.LayoutParams(
                largura,
                altura,
                tipoJanela,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or
                            Gravity.START

                x = xSalvo
                y = ySalvo
            }

        janela = painel

        try {

            windowManager.addView(
                painel,
                parametros
            )

        } catch (_: Exception) {

            janela = null
        }
    }

    private fun criarPainel(): View {

        if (!expandido) {
            return criarBolha()
        }

        val raiz =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(6),
                    dp(6),
                    dp(6),
                    dp(6)
                )

                background =
                    criarFundo(
                        Color.argb(
                            205,
                            20,
                            24,
                            30
                        ),
                        dp(18)
                    )

                elevation =
                    dp(10).toFloat()
            }

        val cabecalho =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(6),
                    dp(3),
                    dp(3),
                    dp(5)
                )
            }

        val arrastar =
            TextView(this).apply {

                text = "≡"

                textSize = 22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(32),
                        dp(38)
                    )

                background =
                    criarFundo(
                        Color.argb(
                            90,
                            255,
                            255,
                            255
                        ),
                        dp(12)
                    )
            }

        val titulo =
            TextView(this).apply {

                text = "MARCADOR"

                textSize = 13f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(38),
                        1f
                    ).apply {
                        leftMargin = dp(7)
                    }
            }

        val fechar =
            TextView(this).apply {

                text = "X"

                textSize = 15f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                    )

                background =
                    criarFundo(
                        Color.argb(
                            110,
                            220,
                            50,
                            50
                        ),
                        dp(12)
                    )

                setOnClickListener {
                    stopSelf()
                }
            }

        cabecalho.addView(
            arrastar
        )

        cabecalho.addView(
            titulo
        )

        cabecalho.addView(
            fechar
        )

        raiz.addView(
            cabecalho
        )

        configurarArraste(
            arrastar
        )

        val scroll =
            ScrollView(this).apply {

                isVerticalScrollBarEnabled =
                    false

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(178),
                        dp(300)
                    )
            }

        val lista =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        val categorias =
            MarcadorRepository.carregarCategorias(
                this
            )

        categorias.forEach { categoria ->

            lista.addView(
                criarBotaoCategoria(
                    categoria
                )
            )
        }

        if (categorias.isEmpty()) {

            lista.addView(
                TextView(this).apply {

                    text =
                        "Nenhuma categoria cadastrada"

                    textSize =
                        12f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        Color.WHITE
                    )

                    setPadding(
                        dp(8),
                        dp(18),
                        dp(8),
                        dp(18)
                    )
                }
            )
        }

        scroll.addView(
            lista
        )

        raiz.addView(
            scroll
        )

        return raiz
    }

    private fun criarBotaoCategoria(
        categoria: CategoriaFlutuante
    ): Button {

        val cor =
            categoria.cor or
                    0xFF000000.toInt()

        val corFundo =
            Color.argb(
                205,
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
            )

        val texto =
            if (corContraste(cor)) {
                Color.BLACK
            } else {
                Color.WHITE
            }

        return Button(this).apply {

            text =
                categoria.nome

            textSize =
                14f

            isAllCaps =
                false

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                texto
            )

            gravity =
                Gravity.CENTER

            setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
            )

            background =
                criarFundo(
                    corFundo,
                    dp(13)
                )

            elevation =
                dp(3).toFloat()

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(178),
                    dp(48)
                ).apply {

                    bottomMargin =
                        dp(5)
                }

            alpha =
                0.92f

            setOnClickListener {

                marcarCategoria(
                    categoria
                )
            }
        }
    }

    private fun criarBolha(): View {

        val bolha =
            TextView(this).apply {

                text =
                    "M"

                textSize =
                    20f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                background =
                    criarFundo(
                        Color.argb(
                            215,
                            21,
                            101,
                            192
                        ),
                        dp(30)
                    )

                elevation =
                    dp(10).toFloat()

                alpha =
                    0.90f

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                    )
            }

        bolha.setOnClickListener {

            expandido = true

            salvarEstado()

            reconstruirJanela()
        }

        configurarArraste(
            bolha
        )

        return bolha
    }

    private fun configurarArraste(
        handle: View
    ) {

        var inicialX = 0
        var inicialY = 0
        var toqueX = 0f
        var toqueY = 0f
        var movendo = false

        handle.setOnTouchListener { _, evento ->

            val p =
                parametros ?: return@setOnTouchListener true

            when (evento.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    inicialX = p.x
                    inicialY = p.y

                    toqueX =
                        evento.rawX

                    toqueY =
                        evento.rawY

                    movendo =
                        false

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        (evento.rawX - toqueX).toInt()

                    val dy =
                        (evento.rawY - toqueY).toInt()

                    if (abs(dx) > dp(3) ||
                        abs(dy) > dp(3)
                    ) {

                        movendo =
                            true
                    }

                    if (movendo) {

                        p.x =
                            inicialX + dx

                        p.y =
                            inicialY + dy

                        try {

                            windowManager.updateViewLayout(
                                janela,
                                p
                            )

                        } catch (_: Exception) {
                        }
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    salvarPosicao()

                    true
                }

                else -> true
            }
        }
    }

    private fun marcarCategoria(
        categoria: CategoriaFlutuante
    ) {

        val location =
            ultimaLocalizacao

        if (location == null) {

            Toast.makeText(
                this,
                "Aguardando GPS...",
                Toast.LENGTH_SHORT
            ).show()

            iniciarLocalizacao()

            return
        }

        val idade =
            idadeLocalizacaoSegundos(
                location
            )

        if (idade > 15) {

            Toast.makeText(
                this,
                "GPS desatualizado. Aguarde...",
                Toast.LENGTH_SHORT
            ).show()

            iniciarLocalizacao()

            return
        }

        when (
            val resultado =
                MarcadorRepository.salvarPonto(
                    this,
                    categoria,
                    location
                )
        ) {

            is MarcadorRepository.ResultadoSalvar.Sucesso -> {

                Toast.makeText(
                    this,
                    "${categoria.nome} salvo | total: ${resultado.total}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            is MarcadorRepository.ResultadoSalvar.Erro -> {

                Toast.makeText(
                    this,
                    resultado.mensagem,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun idadeLocalizacaoSegundos(
        location: Location
    ): Long {

        return try {

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.JELLY_BEAN_MR1
            ) {

                val nanos =
                    android.os.SystemClock
                        .elapsedRealtimeNanos() -
                            location.elapsedRealtimeNanos

                if (nanos <= 0L) {
                    0L
                } else {
                    nanos /
                            1_000_000_000L
                }

            } else {

                0L
            }

        } catch (_: Exception) {

            0L
        }
    }

    private fun reconstruirJanela() {

        val antiga =
            janela

        val p =
            parametros

        if (antiga != null) {

            try {
                windowManager.removeView(
                    antiga
                )
            } catch (_: Exception) {
            }
        }

        janela = null

        if (p != null) {

            p.width =
                dp(
                    if (expandido) {
                        190
                    } else {
                        62
                    }
                )

            janela =
                criarPainel()

            try {

                windowManager.addView(
                    janela,
                    p
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun salvarPosicao() {

        val p =
            parametros ?: return

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putInt(
                KEY_X,
                p.x
            )
            .putInt(
                KEY_Y,
                p.y
            )
            .putBoolean(
                KEY_EXPANDED,
                expandido
            )
            .apply()
    }

    private fun salvarEstado() {

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_EXPANDED,
                expandido
            )
            .apply()
    }

    private fun criarFundo(
        cor: Int,
        raio: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(cor)

            cornerRadius =
                raio.toFloat()

            setStroke(
                dp(1),
                Color.argb(
                    80,
                    255,
                    255,
                    255
                )
            )
        }
    }

    private fun corContraste(
        cor: Int
    ): Boolean {

        val luminosidade =
            (
                0.299 *
                        Color.red(cor) +
                        0.587 *
                        Color.green(cor) +
                        0.114 *
                        Color.blue(cor)
                ) / 255.0

        return luminosidade > 0.60
    }

    private fun dp(
        valor: Int
    ): Int {

        return (
            valor *
                    resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroy() {

        try {

            locationManager.removeUpdates(
                locationListener
            )

        } catch (_: Exception) {
        }

        val view =
            janela

        if (view != null) {

            try {

                windowManager.removeView(
                    view
                )

            } catch (_: Exception) {
            }
        }

        janela = null

        super.onDestroy()
    }
}
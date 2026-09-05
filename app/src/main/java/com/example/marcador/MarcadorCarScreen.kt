package com.example.marcador

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

import org.json.JSONArray
import org.json.JSONObject

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarcadorCarScreen(
    carContext: CarContext
) : Screen(carContext) {

    companion object {
        private const val PREFS = "marcador_preferencias"
        private const val KEY_CATEGORIAS = "categorias"
        private const val KEY_PONTOS = "pontos"
    }

    private val locationManager =
        carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var ultimaLocalizacao: Location? = null

    private var mensagem = "Obtendo GPS..."

    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {

            if (!location.latitude.isFinite() ||
                !location.longitude.isFinite()
            ) {
                return
            }

            ultimaLocalizacao = Location(location)

            mensagem = if (location.accuracy > 0f) {
                String.format(
                    Locale.US,
                    "GPS OK | precisao %.1f m",
                    location.accuracy
                )
            } else {
                "GPS OK"
            }

            invalidate()
        }

        override fun onProviderEnabled(provider: String) {

            if (provider == LocationManager.GPS_PROVIDER) {
                mensagem = "GPS ativo"
                invalidate()
            }
        }

        override fun onProviderDisabled(provider: String) {

            if (provider == LocationManager.GPS_PROVIDER) {
                mensagem = "GPS desativado"
                invalidate()
            }
        }
    }

    init {
        iniciarGPS()
    }

    override fun onGetTemplate(): Template {

        val categorias = carregarCategorias()
        val totalPontos = contarPontos()

        val listaBuilder = ItemList.Builder()

        categorias.forEach { categoria ->

            val item = GridItem.Builder()
                .setTitle(categoria.nome)
                .setText("Marcar ponto")
                .setImage(
                    criarIconeCategoria(categoria.cor),
                    GridItem.IMAGE_TYPE_ICON
                )
                .setOnClickListener {
                    marcarPonto(categoria)
                }
                .build()

            listaBuilder.addItem(item)
        }

        val lista = listaBuilder.build()

        val titulo = if (totalPontos == 1) {
            "Marcador | 1 ponto"
        } else {
            "Marcador | $totalPontos pontos"
        }

        return GridTemplate.Builder()
            .setTitle(titulo)
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(lista)
            .setLoading(false)
            .build()
    }

    private fun carregarCategorias(): List<CategoriaCar> {

        val prefs = carContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val texto = prefs.getString(
            KEY_CATEGORIAS,
            null
        )

        if (texto.isNullOrBlank()) {
            return categoriasPadrao()
        }

        return try {

            val array = JSONArray(texto)
            val categorias = mutableListOf<CategoriaCar>()

            for (i in 0 until array.length()) {

                val obj = array.getJSONObject(i)

                val nome = obj.optString("nome").trim()
                val id = obj.optString("id").trim()

                if (nome.isNotEmpty()) {

                    val cor = obj.optInt(
                        "cor",
                        Color.rgb(33, 150, 243)
                    )

                    categorias.add(
                        CategoriaCar(
                            nome = nome,
                            id = if (id.isNotEmpty()) {
                                id
                            } else {
                                gerarIdCategoria(nome)
                            },
                            cor = cor
                        )
                    )
                }
            }

            if (categorias.isNotEmpty()) {
                categorias
            } else {
                categoriasPadrao()
            }

        } catch (_: Exception) {

            categoriasPadrao()
        }
    }

    private fun categoriasPadrao(): List<CategoriaCar> {

        return listOf(
            CategoriaCar(
                "Quebra-mola",
                "quebra_mola",
                Color.rgb(230, 81, 0)
            ),
            CategoriaCar(
                "Posto",
                "posto",
                Color.rgb(56, 142, 60)
            ),
            CategoriaCar(
                "Radar",
                "radar",
                Color.rgb(211, 47, 47)
            ),
            CategoriaCar(
                "Perigo",
                "perigo",
                Color.rgb(249, 168, 37)
            )
        )
    }

    private fun gerarIdCategoria(nome: String): String {

        return nome
            .lowercase(Locale.ROOT)
            .normalizeSemAcentos()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun String.normalizeSemAcentos(): String {

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

    private fun criarIconeCategoria(cor: Int): CarIcon {

        val iconCompat = IconCompat.createWithResource(
            carContext,
            R.drawable.ic_car_marker
        )

        val corClara = cor or 0xFF000000.toInt()

        val corEscura = escurecerCor(corClara)

        val carColor = CarColor.createCustom(
            corClara,
            corEscura
        )

        return CarIcon.Builder(iconCompat)
            .setTint(carColor)
            .build()
    }

    private fun escurecerCor(cor: Int): Int {

        val r = (Color.red(cor) * 0.65f).toInt()
        val g = (Color.green(cor) * 0.65f).toInt()
        val b = (Color.blue(cor) * 0.65f).toInt()

        return Color.rgb(r, g, b)
    }

    private fun marcarPonto(categoria: CategoriaCar) {

        if (!temPermissaoLocalizacao()) {

            mensagem = "Permissao de localizacao necessaria"
            invalidate()

            carContext.requestPermissions(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) { _, _ ->
                iniciarGPS()
            }

            return
        }

        val location = ultimaLocalizacao

        if (location == null) {

            mensagem = "Aguardando GPS..."
            invalidate()

            iniciarGPS()
            return
        }

        if (!location.latitude.isFinite() ||
            !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) {

            mensagem = "Coordenada GPS invalida"
            invalidate()

            return
        }

        if (!location.accuracy.isFinite() ||
            location.accuracy <= 0f
        ) {

            mensagem = "Aguardando precisao GPS..."
            invalidate()

            return
        }

        if (location.accuracy > 100f) {

            mensagem = String.format(
                Locale.US,
                "Precisao insuficiente: %.1f m",
                location.accuracy
            )

            invalidate()

            return
        }

        try {

            val data = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val latitude = location.latitude
            val longitude = location.longitude

            /*
             * IMPORTANTE:
             * O JSON abaixo segue exatamente o padrao
             * utilizado pelo Ponto.toJson() do aplicativo.
             */

            val novoPonto = JSONObject().apply {

                put("nome", categoria.nome)

                put(
                    "latitude",
                    latitude
                )

                put(
                    "longitude",
                    longitude
                )

                put(
                    "coordenadas",
                    formatarCoordenadas(
                        latitude,
                        longitude
                    )
                )

                put(
                    "permalink",
                    gerarLinkWaze(
                        latitude,
                        longitude
                    )
                )

                put(
                    "data",
                    data
                )

                put(
                    "precisao",
                    location.accuracy.toDouble()
                )

                put(
                    "provedor",
                    location.provider ?: ""
                )

                put(
                    "satelites",
                    obterQuantidadeSatelites(location)
                )
            }

            val prefs = carContext.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

            val textoExistente = prefs.getString(
                KEY_PONTOS,
                null
            )

            val array = if (!textoExistente.isNullOrBlank()) {

                try {
                    JSONArray(textoExistente)
                } catch (_: Exception) {
                    JSONArray()
                }

            } else {

                JSONArray()
            }

            array.put(novoPonto)

            val salvo = prefs.edit()
                .putString(
                    KEY_PONTOS,
                    array.toString()
                )
                .commit()

            if (salvo) {

                val total = array.length()

                mensagem = String.format(
                    Locale.US,
                    "%s salvo | total: %d",
                    categoria.nome,
                    total
                )

            } else {

                mensagem = "Erro ao salvar ponto"
            }

            /*
             * Forca o Android Auto a reconstruir a tela.
             * Assim o contador do titulo muda imediatamente.
             */
            invalidate()

        } catch (e: Exception) {

            mensagem =
                "Erro ao salvar: ${e.message ?: "desconhecido"}"

            invalidate()
        }
    }

    private fun contarPontos(): Int {

        val prefs = carContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val texto = prefs.getString(
            KEY_PONTOS,
            null
        )

        if (texto.isNullOrBlank()) {
            return 0
        }

        return try {

            JSONArray(texto).length()

        } catch (_: Exception) {

            0
        }
    }

    private fun formatarCoordenadas(
        latitude: Double,
        longitude: Double
    ): String {

        return String.format(
            Locale.US,
            "%.5f, %.5f",
            latitude,
            longitude
        )
    }

    private fun gerarLinkWaze(
        latitude: Double,
        longitude: Double
    ): String {

        val lat = String.format(
            Locale.US,
            "%.5f",
            latitude
        )

        val lon = String.format(
            Locale.US,
            "%.5f",
            longitude
        )

        return "https://www.waze.com/pt-BR/editor?env=row&lat=$lat&lon=$lon&marker=true&zoomLevel=20"
    }

    private fun iniciarGPS() {

        if (!temPermissaoLocalizacao()) {

            mensagem = "Permissao de localizacao necessaria"
            invalidate()

            return
        }

        try {

            val gps =
                locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                )

            if (gps != null) {

                ultimaLocalizacao = Location(gps)

                mensagem = if (gps.accuracy > 0f) {

                    String.format(
                        Locale.US,
                        "GPS OK | precisao %.1f m",
                        gps.accuracy
                    )

                } else {

                    "GPS OK"
                }

                invalidate()
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0f,
                locationListener,
                Looper.getMainLooper()
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
                    Looper.getMainLooper()
                )
            }

        } catch (_: SecurityException) {

            mensagem = "Sem permissao GPS"
            invalidate()

        } catch (_: Exception) {

            mensagem = "Erro GPS"
            invalidate()
        }
    }

    private fun temPermissaoLocalizacao(): Boolean {

        return ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    carContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun obterQuantidadeSatelites(
        location: Location
    ): Int {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            location.extras?.let {

                if (it.containsKey("satellites")) {

                    return it.getInt(
                        "satellites",
                        0
                    )
                }
            }
        }

        return 0
    }

    data class CategoriaCar(
        val nome: String,
        val id: String,
        val cor: Int
    )
}
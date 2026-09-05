package com.example.marcador

import android.content.Context
import android.graphics.Color
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategoriaFlutuante(
    val nome: String,
    val id: String,
    val cor: Int
)

object MarcadorRepository {

    private const val PREFS = "marcador_preferencias"
    private const val KEY_CATEGORIAS = "categorias"
    private const val KEY_PONTOS = "pontos"

    private val CORES_PADRAO = listOf(
        Color.rgb(211, 47, 47),
        Color.rgb(230, 81, 0),
        Color.rgb(249, 168, 37),
        Color.rgb(56, 142, 60),
        Color.rgb(0, 151, 167),
        Color.rgb(21, 101, 192),
        Color.rgb(94, 53, 177),
        Color.rgb(216, 27, 96),
        Color.rgb(93, 64, 55),
        Color.rgb(97, 97, 97),
        Color.rgb(0, 121, 107),
        Color.rgb(191, 54, 12)
    )

    fun carregarCategorias(context: Context): List<CategoriaFlutuante> {

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val texto = prefs.getString(
            KEY_CATEGORIAS,
            null
        )

        if (texto.isNullOrBlank()) {
            return emptyList()
        }

        return try {

            val array = JSONArray(texto)
            val resultado = mutableListOf<CategoriaFlutuante>()

            for (i in 0 until array.length()) {

                val obj = array.getJSONObject(i)

                val nome = obj.optString("nome").trim()

                if (nome.isEmpty()) {
                    continue
                }

                val idInformado =
                    obj.optString("id").trim()

                val id = if (idInformado.isNotEmpty()) {
                    idInformado
                } else {
                    gerarId(nome)
                }

                val cor = if (obj.has("cor")) {
                    obj.optInt(
                        "cor",
                        CORES_PADRAO[i % CORES_PADRAO.size]
                    )
                } else {
                    CORES_PADRAO[i % CORES_PADRAO.size]
                }

                resultado.add(
                    CategoriaFlutuante(
                        nome = nome,
                        id = id,
                        cor = cor
                    )
                )
            }

            resultado

        } catch (_: Exception) {

            emptyList()
        }
    }

    fun salvarPonto(
        context: Context,
        categoria: CategoriaFlutuante,
        location: Location
    ): ResultadoSalvar {

        if (!location.latitude.isFinite() ||
            !location.longitude.isFinite()
        ) {
            return ResultadoSalvar.Erro(
                "Coordenada GPS invalida"
            )
        }

        if (location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) {
            return ResultadoSalvar.Erro(
                "Coordenada GPS invalida"
            )
        }

        if (!location.accuracy.isFinite() ||
            location.accuracy <= 0f
        ) {
            return ResultadoSalvar.Erro(
                "Precisao GPS indisponivel"
            )
        }

        if (location.accuracy > 100f) {
            return ResultadoSalvar.Erro(
                String.format(
                    Locale.US,
                    "Precisao insuficiente: %.1f m",
                    location.accuracy
                )
            )
        }

        val data = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val latitude = location.latitude
        val longitude = location.longitude

        val novoPonto = JSONObject().apply {

            put(
                "nome",
                categoria.nome
            )

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

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val existente = prefs.getString(
            KEY_PONTOS,
            null
        )

        val array = if (!existente.isNullOrBlank()) {

            try {
                JSONArray(existente)
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

        if (!salvo) {
            return ResultadoSalvar.Erro(
                "Nao foi possivel salvar"
            )
        }

        return ResultadoSalvar.Sucesso(
            total = array.length(),
            latitude = latitude,
            longitude = longitude
        )
    }

    fun contarPontos(context: Context): Int {

        val prefs = context.getSharedPreferences(
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

    private fun gerarId(nome: String): String {

        return java.text.Normalizer
            .normalize(
                nome.lowercase(Locale.ROOT),
                java.text.Normalizer.Form.NFD
            )
            .replace(
                Regex("\\p{InCombiningDiacriticalMarks}+"),
                ""
            )
            .replace(
                Regex("[^a-z0-9]+"),
                "_"
            )
            .trim('_')
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

    private fun obterQuantidadeSatelites(
        location: Location
    ): Int {

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.N
        ) {

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

    sealed class ResultadoSalvar {

        data class Sucesso(
            val total: Int,
            val latitude: Double,
            val longitude: Double
        ) : ResultadoSalvar()

        data class Erro(
            val mensagem: String
        ) : ResultadoSalvar()
    }
}
package com.homepantry.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalTime
import kotlin.math.roundToInt

// Respuesta de Open-Meteo (https://open-meteo.com/en/docs). Todo anulable y con valores por
// defecto: Gson rellena por reflexión y un campo que falte no debe romper el panel.

data class OpenMeteoCurrent(
    val time: String? = null,
    @SerializedName("temperature_2m") val temperature2m: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null
)

data class OpenMeteoHourly(
    val time: List<String?>? = null,
    @SerializedName("temperature_2m") val temperature2m: List<Double?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int?>? = null
)

data class OpenMeteoDaily(
    val time: List<String?>? = null,
    @SerializedName("temperature_2m_max") val temperature2mMax: List<Double?>? = null,
    @SerializedName("temperature_2m_min") val temperature2mMin: List<Double?>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null
)

data class OpenMeteoForecast(
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OpenMeteoDaily? = null
)

/** Un pueblo del buscador de Open-Meteo; [admin1] es la provincia o comunidad, para distinguir homónimos. */
data class WeatherPlace(
    val name: String = "",
    val admin1: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class OpenMeteoGeocoding(val results: List<WeatherPlace>? = null)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): OpenMeteoForecast
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "es"
    ): OpenMeteoGeocoding
}

/** Open-Meteo no pide clave ni cuenta: solo recibe la ubicación del pueblo elegido. */
object OpenMeteoClient {
    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val forecastApi by lazy { retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java) }
    private val geocodingApi by lazy { retrofit("https://geocoding-api.open-meteo.com/").create(OpenMeteoGeocodingApi::class.java) }

    fun forecast(): OpenMeteoApi = forecastApi
    fun geocoding(): OpenMeteoGeocodingApi = geocodingApi
}

/** Estado del cielo agrupado como se va a dibujar (un icono por grupo). */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Código WMO de Open-Meteo a [WeatherKind]; uno desconocido se dibuja como nublado. */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY_CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}

data class HourWeather(val hour: Int = 0, val temp: Int = 0, val kind: WeatherKind = WeatherKind.CLEAR)

data class WeatherSnapshot(
    val currentTemp: Int = 0,
    val kind: WeatherKind = WeatherKind.CLEAR,
    val maxTemp: Int = 0,
    val minTemp: Int = 0,
    val rainProbability: Int = 0,
    val hours: List<HourWeather> = emptyList(),
    val fetchedAtMillis: Long = 0
)

/** Horas que se dibujan en la fila de la mañana. */
private val PANEL_HOURS = 8..22

/**
 * Lo que enseña el panel a partir de la respuesta de Open-Meteo, o null si falta lo básico (la
 * temperatura actual o la máxima y mínima de hoy). Las horas son solo las de hoy, de 8 a 22, y
 * se saltan las que vengan sin temperatura.
 */
fun weatherSnapshot(forecast: OpenMeteoForecast, fetchedAtMillis: Long): WeatherSnapshot? {
    val current = forecast.current ?: return null
    val daily = forecast.daily ?: return null
    val currentTemp = current.temperature2m ?: return null
    val today = daily.time?.firstOrNull() ?: return null
    val max = daily.temperature2mMax?.firstOrNull() ?: return null
    val min = daily.temperature2mMin?.firstOrNull() ?: return null
    val hourly = forecast.hourly
    val hours = hourly?.time.orEmpty().withIndex().mapNotNull { (index, time) ->
        if (time == null || !time.startsWith("${today}T")) return@mapNotNull null
        val hour = time.substringAfter('T').substringBefore(':').toIntOrNull() ?: return@mapNotNull null
        if (hour !in PANEL_HOURS) return@mapNotNull null
        val temp = hourly?.temperature2m?.getOrNull(index) ?: return@mapNotNull null
        HourWeather(hour, temp.roundToInt(), weatherKind(hourly.weatherCode?.getOrNull(index) ?: -1))
    }
    return WeatherSnapshot(
        currentTemp = currentTemp.roundToInt(),
        kind = weatherKind(current.weatherCode ?: -1),
        maxTemp = max.roundToInt(),
        minTemp = min.roundToInt(),
        rainProbability = daily.precipitationProbabilityMax?.firstOrNull() ?: 0,
        hours = hours,
        fetchedAtMillis = fetchedAtMillis
    )
}

/** Por la mañana el tiempo va en grande, con las horas: es cuando se decide qué ponerse. */
fun isMorning(time: LocalTime): Boolean = time.hour < 12

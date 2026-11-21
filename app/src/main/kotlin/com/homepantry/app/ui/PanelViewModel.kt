package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MenuSlotSection
import com.homepantry.app.data.OpenMeteoClient
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.WeatherPlace
import com.homepantry.app.data.WeatherSnapshot
import com.homepantry.app.data.todayMenuSections
import com.homepantry.app.data.weatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** El tiempo del panel: el pueblo elegido, el último dato leído y si la última lectura falló. */
data class WeatherState(
    val place: WeatherPlace? = null,
    val snapshot: WeatherSnapshot? = null,
    val failed: Boolean = false
)

/** Cada cuánto se vuelve a pedir el tiempo: más a menudo no aporta nada en una cocina. */
private const val WEATHER_REFRESH_MILLIS = 30 * 60_000L

/**
 * Menú de hoy y tiempo del panel de la cocina. Los productos y las zonas los da [AppViewModel].
 * Todo cuelga de [now], que avanza cada minuto: así a medianoche el menú pasa solo al día
 * siguiente aunque la tablet lleve días sin reiniciar la app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(
    private val entriesRepository: MealEntriesRepository,
    private val userPrefs: UserPrefs
) : ViewModel() {

    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            val current = LocalDateTime.now()
            emit(current)
            // Hasta el siguiente minuto en punto, para que el reloj no vaya con retraso.
            delay(ChronoUnit.MILLIS.between(current, current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).coerceAtLeast(1))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    val menu: StateFlow<List<MenuSlotSection>> = now
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            entriesRepository.observeRange(day.toString(), day.toString())
                .map { todayMenuSections(it, day) }
                // Sin red o sin permisos el panel sigue en pie; el menú se queda vacío.
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefs.weatherPlace.collectLatest { place ->
                _weather.value = WeatherState(place = place)
                if (place == null) return@collectLatest
                while (true) {
                    fetchWeather(place)
                    delay(WEATHER_REFRESH_MILLIS)
                }
            }
        }
    }

    /** Vuelve a pedir el tiempo ahora (al tocar el aviso de «sin datos»). */
    fun refreshWeather() {
        val place = _weather.value.place ?: return
        viewModelScope.launch { fetchWeather(place) }
    }

    private suspend fun fetchWeather(place: WeatherPlace) {
        val snapshot = try {
            weatherSnapshot(OpenMeteoClient.forecast().forecast(place.latitude, place.longitude), System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // Un fallo deja a la vista el último dato bueno, con su hora.
        _weather.value = if (snapshot != null) {
            WeatherState(place, snapshot, failed = false)
        } else {
            _weather.value.copy(failed = true)
        }
    }

    /** Pueblos que coinciden con [query] en el buscador de Open-Meteo; lanza si no hay red. */
    suspend fun searchPlaces(query: String): List<WeatherPlace> =
        OpenMeteoClient.geocoding().search(query.trim()).results.orEmpty()
}

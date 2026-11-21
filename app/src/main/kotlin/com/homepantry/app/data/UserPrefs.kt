package com.homepantry.app.data

import android.content.Context
import com.google.gson.Gson
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

private const val MAX_NOTIFIED_IDS = 20

/**
 * Persistencia local ligera del código de casa y el nombre del usuario
 * (SPEC.md §1.1) — se piden una sola vez, no hay sistema de cuentas.
 */
class UserPrefs(private val context: Context) {
    private val householdCodeKey = stringPreferencesKey("household_code")
    private val userNameKey = stringPreferencesKey("user_name")
    // Lista separada por comas: los ids de Firestore no llevan comas y así el orden se conserva.
    private val notifiedOfferIdsKey = stringPreferencesKey("notified_offer_ids")
    private val comedorFingerprintKey = stringPreferencesKey("comedor_pdf_fingerprint")
    private val cardReadModeKey = stringPreferencesKey("card_read_mode")
    private val panelModeKey = booleanPreferencesKey("panel_mode")
    private val weatherPlaceKey = stringPreferencesKey("weather_place")

    val householdCode: Flow<String?> = context.dataStore.data.map { it[householdCodeKey] }
    val userName: Flow<String?> = context.dataStore.data.map { it[userNameKey] }

    suspend fun saveHousehold(code: String) {
        context.dataStore.edit { it[householdCodeKey] = normalizeHouseholdCode(code) }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name.trim() }
    }

    /** Avisos de ofertas ya notificados en este móvil, para que el worker no repita la notificación. */
    val notifiedOfferIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[notifiedOfferIdsKey]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    /** Guarda como mucho los [MAX_NOTIFIED_IDS] últimos: los avisos se borran a las 4 semanas. */
    suspend fun addNotifiedOfferId(id: String) {
        context.dataStore.edit { prefs ->
            val ids = prefs[notifiedOfferIdsKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            prefs[notifiedOfferIdsKey] = (ids - id + id).takeLast(MAX_NOTIFIED_IDS).joinToString(",")
        }
    }

    /** Huella del último PDF del comedor leído con Gemini, para no volver a leer el mismo. */
    val comedorFingerprint: Flow<String?> = context.dataStore.data.map { it[comedorFingerprintKey] }

    suspend fun saveComedorFingerprint(value: String) {
        context.dataStore.edit { it[comedorFingerprintKey] = value }
    }

    /** Último modo con el que se leyeron capturas del banco en este móvil; null si nunca o desconocido. */
    val cardReadMode: Flow<CardReadMode?> = context.dataStore.data.map { prefs ->
        prefs[cardReadModeKey]?.let { name -> CardReadMode.entries.firstOrNull { it.name == name } }
    }

    suspend fun saveCardReadMode(mode: CardReadMode) {
        context.dataStore.edit { it[cardReadModeKey] = mode.name }
    }

    /** Este dispositivo es el panel de la cocina: la app arranca en el panel (solo en este dispositivo). */
    val panelMode: Flow<Boolean> = context.dataStore.data.map { it[panelModeKey] ?: false }

    suspend fun setPanelMode(enabled: Boolean) {
        context.dataStore.edit { it[panelModeKey] = enabled }
    }

    /** Pueblo del tiempo del panel; null si no se ha elegido o el JSON guardado no se puede leer. */
    val weatherPlace: Flow<WeatherPlace?> = context.dataStore.data.map { prefs ->
        prefs[weatherPlaceKey]?.let { json ->
            try {
                Gson().fromJson(json, WeatherPlace::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveWeatherPlace(place: WeatherPlace) {
        context.dataStore.edit { it[weatherPlaceKey] = Gson().toJson(place) }
    }

    /** Borra el código de casa guardado -- MainActivity reacciona mostrando JoinHouseholdScreen. */
    suspend fun clearHousehold() {
        context.dataStore.edit { it.remove(householdCodeKey) }
    }
}

package com.homepantry.app.data

import android.content.Context
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

    /** Borra el código de casa guardado -- MainActivity reacciona mostrando JoinHouseholdScreen. */
    suspend fun clearHousehold() {
        context.dataStore.edit { it.remove(householdCodeKey) }
    }
}

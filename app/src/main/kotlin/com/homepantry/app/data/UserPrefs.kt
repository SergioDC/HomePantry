package com.homepantry.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * Persistencia local ligera del código de casa y el nombre del usuario
 * (SPEC.md §1.1) — se piden una sola vez, no hay sistema de cuentas.
 */
class UserPrefs(private val context: Context) {
    private val householdCodeKey = stringPreferencesKey("household_code")
    private val userNameKey = stringPreferencesKey("user_name")

    val householdCode: Flow<String?> = context.dataStore.data.map { it[householdCodeKey] }
    val userName: Flow<String?> = context.dataStore.data.map { it[userNameKey] }

    suspend fun saveHousehold(code: String) {
        context.dataStore.edit { it[householdCodeKey] = normalizeHouseholdCode(code) }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name.trim() }
    }

    /** Borra el código de casa guardado -- MainActivity reacciona mostrando JoinHouseholdScreen. */
    suspend fun clearHousehold() {
        context.dataStore.edit { it.remove(householdCodeKey) }
    }
}

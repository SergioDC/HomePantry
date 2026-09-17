package com.homepantry.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_FILE_NAME = "gemini_api_key_store"
private const val KEY_API_KEY = "api_key"

/**
 * Almacén cifrado (Android Keystore vía EncryptedSharedPreferences) de la
 * API key de Gemini que cada usuario aporta -- a diferencia de [UserPrefs]
 * (DataStore en texto plano para nombre/código de casa), esto es una
 * credencial real y nunca se sincroniza entre miembros del hogar.
 */
class GeminiApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Nunca lanza: si el fichero cifrado no se puede descifrar (corrupto, o
     * restaurado por Auto Backup / transferencia de dispositivo sobre una
     * instalación cuya master key del Keystore ya no coincide), se borra el
     * fichero y se trata como "no hay key configurada". Esto se llama en cada
     * escaneo de ticket, incluso para usuarios que nunca han configurado nada,
     * así que una excepción aquí tumbaría la corrutina del escaneo.
     */
    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        try {
            prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            null
        }
    }

    suspend fun save(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
    }
}

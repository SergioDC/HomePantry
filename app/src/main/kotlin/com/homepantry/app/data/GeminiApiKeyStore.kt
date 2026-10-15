package com.homepantry.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fichero de EncryptedSharedPreferences de versiones anteriores; solo se lee para migrar (Tarea 8 lo quita). */
private const val LEGACY_PREFS_FILE_NAME = "gemini_api_key_store"
private const val PREFS_FILE_NAME = "gemini_settings"
private const val KEY_API_KEY = "api_key"
private const val KEY_API_KEY_ENCRYPTED = "api_key_encrypted"
private const val KEY_MODEL = "model"
private const val KEYSTORE_ALIAS = "gemini_api_key"

/**
 * API key de Gemini que aporta cada usuario, cifrada con el Android Keystore
 * ([KeystoreSecretStorage]); el modelo elegido no es secreto y va sin cifrar. Nunca se
 * sincroniza entre miembros del hogar. Las lecturas nunca lanzan: se llaman en cada escaneo de
 * ticket, también para quien nunca configuró nada.
 */
class GeminiApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }
    private val apiKey by lazy { KeystoreSecretStorage(prefs, KEY_API_KEY_ENCRYPTED, KEYSTORE_ALIAS) }
    private val model by lazy { PlainPrefStorage(prefs, KEY_MODEL) }

    @Volatile
    private var migrated = false

    /**
     * Copia la clave y el modelo del almacén antiguo (si existe) y borra su fichero. Idempotente. Si el
     * almacén antiguo no se puede abrir ahora, no se borra nada y se reintenta en la siguiente llamada.
     */
    private fun migrateLegacyOnce() {
        if (migrated) return
        if (File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_FILE_NAME.xml").exists()) {
            val pairs = listOf(
                LegacyEncryptedStore(context, KEY_API_KEY) to apiKey,
                LegacyEncryptedStore(context, KEY_MODEL) to model
            )
            if (!migrateAllSecrets(pairs)) return
            context.deleteSharedPreferences(LEGACY_PREFS_FILE_NAME)
        }
        migrated = true
    }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        try {
            migrateLegacyOnce()
            apiKey.read()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Mismo criterio de "nunca lanza" que [getApiKey]: sin modelo guardado, el modelo por defecto. */
    suspend fun getModel(): String = withContext(Dispatchers.IO) {
        try {
            migrateLegacyOnce()
            model.read()?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        } catch (e: Exception) {
            DEFAULT_GEMINI_MODEL
        }
    }

    suspend fun save(key: String, model: String) = withContext(Dispatchers.IO) {
        migrateLegacyOnce()
        apiKey.write(key.trim())
        this@GeminiApiKeyStore.model.write(model.trim().ifBlank { DEFAULT_GEMINI_MODEL })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        // Migrar antes de borrar: si no, la siguiente lectura traería de vuelta la clave antigua.
        migrateLegacyOnce()
        apiKey.clear()
        model.clear()
    }
}

/**
 * Lectura del almacén antiguo, solo para la migración. Si no se puede abrir o leer, [read] lanza (en
 * vez de devolver null como si estuviera vacío) para que [migrateAllSecrets] no dé por migrado algo
 * que no ha copiado.
 */
@Suppress("DEPRECATION")
private class LegacyEncryptedStore(private val context: Context, private val key: String) : SecretStorage {
    private val prefs: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_FILE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun read(): String? {
        val opened = prefs ?: throw IllegalStateException("No se pudo abrir el almacén antiguo")
        return opened.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    override fun write(value: String) = throw UnsupportedOperationException("El almacén antiguo es de solo lectura")

    override fun clear() {
        try {
            prefs?.edit { remove(key) }
        } catch (e: Exception) {
            // El fichero entero se borra justo después en migrateLegacyOnce.
        }
    }
}

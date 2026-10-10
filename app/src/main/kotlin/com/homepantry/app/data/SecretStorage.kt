package com.homepantry.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Un único valor de texto guardado en el móvil (cifrado o no, según la implementación). */
interface SecretStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

/**
 * Pasa un valor del almacén antiguo al nuevo una sola vez: si el nuevo está vacío se copia el
 * antiguo, y el antiguo se borra siempre que tenga algo (el nuevo manda si ambos tienen valor).
 * @return el valor que queda en el almacén nuevo.
 */
fun migrateSecret(legacy: SecretStorage, target: SecretStorage): String? {
    val current = target.read()
    val old = legacy.read()
    if (current == null && old != null) target.write(old)
    if (old != null) legacy.clear()
    return current ?: old
}

/** Valor sin cifrar en SharedPreferences: para el modelo de Gemini, que no es secreto. */
class PlainPrefStorage(private val prefs: SharedPreferences, private val key: String) : SecretStorage {
    override fun read(): String? = prefs.getString(key, null)
    override fun write(value: String) = prefs.edit { putString(key, value) }
    override fun clear() = prefs.edit { remove(key) }
}

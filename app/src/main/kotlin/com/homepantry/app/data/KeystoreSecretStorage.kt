package com.homepantry.app.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_BITS = 128

/**
 * Fallos al descifrar que significan que el valor guardado no se podrá recuperar nunca (y se puede
 * borrar): etiqueta GCM que no cuadra, Base64 o IV corruptos, clave del Keystore invalidada. Cualquier
 * otro (Keystore ocupado, fallo del proveedor) puede ser pasajero y no debe borrar nada.
 */
internal fun isPermanentDecryptFailure(e: Exception): Boolean =
    e is AEADBadTagException || e is IllegalArgumentException || e is KeyPermanentlyInvalidatedException

/**
 * Valor cifrado con AES-GCM y una clave del Android Keystore (no sale del móvil), guardado en
 * SharedPreferences como Base64(iv + texto cifrado). Sustituye a EncryptedSharedPreferences,
 * obsoleta. Nunca lanza al leer: si el valor no se puede descifrar (copia restaurada en otro móvil,
 * clave del Keystore borrada), se borra y cuenta como "no hay valor".
 */
class KeystoreSecretStorage(
    private val prefs: SharedPreferences,
    private val prefKey: String,
    private val keyAlias: String
) : SecretStorage {

    override fun read(): String? {
        val stored = prefs.getString(prefKey, null) ?: return null
        // Keystore no disponible ahora mismo (algunos Samsung tras arrancar): sin valor esta vez, pero
        // no se borra, porque el valor guardado sigue siendo válido.
        val key = try {
            existingKey()
        } catch (e: Exception) {
            return null
        }
        if (key == null) {
            // La clave del Keystore ya no existe: el valor no se podrá descifrar nunca.
            clear()
            return null
        }
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_SIZE))
            String(cipher.doFinal(bytes, GCM_IV_SIZE, bytes.size - GCM_IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            if (isPermanentDecryptFailure(e)) clear()
            null
        }
    }

    override fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(prefKey, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
    }

    override fun clear() = prefs.edit { remove(prefKey) }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }
        .generateKey()
}

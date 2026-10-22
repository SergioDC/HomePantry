package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Resultado de subir una foto de reparto: URL para pintarla y ruta para poder borrarla. */
data class SplitUpload(val url: String, val storagePath: String)

/**
 * Sube fotos de productos a Firebase Storage.
 * households/{householdCode}/photos/{itemId}.jpg (SPEC.md §2)
 * Comprime la imagen (ImageCompressor) antes de subirla (cierre de huecos §6).
 */
class StorageRepository(
    private val storage: FirebaseStorage,
    private val householdCode: String,
    private val context: Context
) {
    suspend fun uploadPhoto(itemId: String, localUri: Uri): String {
        val compressedUri = compressOffMainThread(localUri)
        val ref = storage.reference.child("households/$householdCode/photos/$itemId.jpg")
        ref.putFile(compressedUri).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Sube la foto de un ticket ya escaneado y confirmado.
     * households/{householdCode}/receipts/{batchId}.jpg -- una foto por
     * ticket, referenciada desde cada Purchase generada en ese escaneo.
     */
    suspend fun uploadReceiptPhoto(batchId: String, localUri: Uri): String {
        val compressedUri = compressOffMainThread(localUri)
        val ref = storage.reference.child("households/$householdCode/receipts/$batchId.jpg")
        ref.putFile(compressedUri).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Sube una foto de un mes de reparto.
     * households/{householdCode}/splitMonths/{monthId}/{photoId}.jpg. Devuelve también la ruta,
     * porque para borrar el objeto más tarde hace falta la ruta y no la URL de descarga.
     */
    suspend fun uploadSplitPhoto(monthId: String, photoId: String, localUri: Uri): SplitUpload {
        val compressedUri = compressOffMainThread(localUri)
        val path = "households/$householdCode/splitMonths/$monthId/$photoId.jpg"
        val ref = storage.reference.child(path)
        ref.putFile(compressedUri).await()
        return SplitUpload(url = ref.downloadUrl.await().toString(), storagePath = path)
    }

    /** Borra una foto de reparto. Si el objeto ya no existe (borrado a medias antes), no es un error. */
    suspend fun deleteSplitPhoto(storagePath: String) {
        try {
            storage.reference.child(storagePath).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    /**
     * Decodificar, reescalar y recomprimir una foto de cámara es trabajo pesado y
     * `ImageCompressor.compress` es bloqueante: desde una corrutina de `viewModelScope`
     * (hilo principal) congelaba la pantalla durante todo el proceso.
     */
    private suspend fun compressOffMainThread(localUri: Uri): Uri =
        withContext(Dispatchers.IO) { ImageCompressor.compress(context, localUri) }
}

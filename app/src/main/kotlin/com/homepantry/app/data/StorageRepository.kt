package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
     * Decodificar, reescalar y recomprimir una foto de cámara es trabajo pesado y
     * `ImageCompressor.compress` es bloqueante: desde una corrutina de `viewModelScope`
     * (hilo principal) congelaba la pantalla durante todo el proceso.
     */
    private suspend fun compressOffMainThread(localUri: Uri): Uri =
        withContext(Dispatchers.IO) { ImageCompressor.compress(context, localUri) }
}

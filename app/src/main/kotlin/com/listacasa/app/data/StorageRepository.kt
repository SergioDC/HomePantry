package com.listacasa.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

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
        val compressedUri = ImageCompressor.compress(context, localUri)
        val ref = storage.reference.child("households/$householdCode/photos/$itemId.jpg")
        ref.putFile(compressedUri).await()
        return ref.downloadUrl.await().toString()
    }
}

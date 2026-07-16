package com.listacasa.app.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Sube fotos de productos a Firebase Storage.
 * households/{householdCode}/photos/{itemId}.jpg (SPEC.md §2)
 */
class StorageRepository(
    private val storage: FirebaseStorage,
    private val householdCode: String
) {
    suspend fun uploadPhoto(itemId: String, localUri: Uri): String {
        val ref = storage.reference.child("households/$householdCode/photos/$itemId.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }
}

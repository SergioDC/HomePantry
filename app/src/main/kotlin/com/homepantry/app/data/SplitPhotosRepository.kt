package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Fotos de un mes de reparto: households/{householdCode}/splitMonths/{monthId}/photos.
 * Solo guarda los documentos; los objetos de Storage los sube y borra StorageRepository.
 */
class SplitPhotosRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection(monthId: String) =
        firestore.collection("households").document(householdCode)
            .collection("splitMonths").document(monthId).collection("photos")

    /** Fotos del mes, la más antigua primero (orden en memoria, sin índices). */
    fun observePhotos(monthId: String): Flow<List<SplitPhoto>> = callbackFlow {
        val registration = collection(monthId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val photos = snapshot?.documents?.mapNotNull { it.toObject(SplitPhoto::class.java) } ?: emptyList()
            trySend(photos.sortedBy { it.createdAt })
        }
        awaitClose { registration.remove() }
    }

    /**
     * Id para una foto nueva, generado antes de subirla: el objeto de Storage se nombra con él y
     * el documento solo se crea cuando la subida ha terminado, así no quedan documentos huérfanos.
     */
    fun newPhotoId(monthId: String): String = collection(monthId).document().id

    /** Crea el documento con el id de [photo] (el de [newPhotoId]). */
    suspend fun add(monthId: String, photo: SplitPhoto): String {
        collection(monthId).document(photo.id).set(photo).await()
        return photo.id
    }

    suspend fun delete(monthId: String, photoId: String) {
        collection(monthId).document(photoId).delete().await()
    }
}

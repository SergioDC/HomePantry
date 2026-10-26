package com.homepantry.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Avisos de la casa. Los crea el script semanal de `tools/offers`; la app solo los lee y marca
 * quién los ha leído y qué ofertas se añadieron, cambiando un solo campo con `arrayUnion` para no
 * pisar lo que acabe de hacer otro miembro.
 */
class NotificationsRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("notifications")

    /** Más recientes primero. */
    fun observeNotifications(): Flow<List<AppNotification>> = callbackFlow {
        val registration = collection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(AppNotification::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /** Lectura puntual para el worker, que no tiene pantalla abierta. */
    suspend fun fetchNotifications(): List<AppNotification> =
        collection().get().await().documents.mapNotNull { it.toObject(AppNotification::class.java) }

    suspend fun markRead(id: String, uid: String) {
        collection().document(id).update("readBy", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun markAdded(id: String, keys: List<String>) {
        if (keys.isEmpty()) return
        collection().document(id).update("addedKeys", FieldValue.arrayUnion(*keys.toTypedArray())).await()
    }
}

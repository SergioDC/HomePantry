package com.listacasa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de productos, sincronizado en tiempo real con Firestore.
 * Equivalente al `window.storage` compartido (shared=true) del prototipo web,
 * pero con Firestore en vez del storage de Artifacts.
 */
class ItemsRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("items")

    /** Flow reactivo: emite la lista completa cada vez que cambia algo en Firestore. */
    fun observeItems(): Flow<List<Item>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { it.toObject(Item::class.java) } ?: emptyList()
            trySend(items)
        }
        awaitClose { registration.remove() }
    }

    /** @return el id generado por Firestore para el nuevo producto. */
    suspend fun addItem(item: Item): String {
        val ref = collection().add(item).await()
        return ref.id
    }

    suspend fun updateItem(item: Item) {
        collection().document(item.id).set(item).await()
    }

    suspend fun updatePhotoUrl(itemId: String, photoUrl: String) {
        collection().document(itemId).update("photoUrl", photoUrl).await()
    }

    suspend fun deleteItem(itemId: String) {
        collection().document(itemId).delete().await()
    }

    suspend fun clearDone(doneItemIds: List<String>) {
        val batch = firestore.batch()
        doneItemIds.forEach { id -> batch.delete(collection().document(id)) }
        batch.commit().await()
    }

    suspend fun reassignZone(fromZone: String, toZone: String) {
        val toReassign = collection().whereEqualTo("zone", fromZone).get().await()
        val batch = firestore.batch()
        toReassign.documents.forEach { doc: com.google.firebase.firestore.DocumentSnapshot ->
            batch.update(doc.reference, "zone", toZone)
        }
        batch.commit().await()
    }
}

package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio del histórico de compras, sincronizado en tiempo real con
 * Firestore. Mismo patrón que ItemsRepository, colección independiente:
 * households/{householdCode}/purchases
 */
class PurchasesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("purchases")

    fun observePurchases(): Flow<List<Purchase>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val purchases = snapshot?.documents?.mapNotNull { it.toObject(Purchase::class.java) } ?: emptyList()
            trySend(purchases)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Alta masiva: todas las líneas confirmadas de un mismo ticket se escriben juntas.
     * Devuelve los ids de los documentos creados (para enlazarles la foto después).
     */
    suspend fun addPurchases(purchases: List<Purchase>): List<String> {
        if (purchases.isEmpty()) return emptyList()
        val batch = firestore.batch()
        val ids = purchases.map { purchase ->
            val document = collection().document()
            batch.set(document, purchase)
            document.id
        }
        batch.commit().await()
        return ids
    }

    /** Enlaza la foto del ticket, subida después de guardar las compras, a las líneas de ese ticket. */
    suspend fun attachTicketPhoto(ids: List<String>, photoUrl: String) {
        ids.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.update(collection().document(id), "ticketPhotoUrl", photoUrl) }
            batch.commit().await()
        }
    }

    /** Borra las compras indicadas; Firestore admite como máximo 500 operaciones por lote. */
    suspend fun deletePurchases(ids: List<String>) {
        ids.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.delete(collection().document(id)) }
            batch.commit().await()
        }
    }

    /** Deshace un borrado: vuelve a escribir la compra en su mismo documento. */
    suspend fun restorePurchase(purchase: Purchase) {
        collection().document(purchase.id).set(purchase).await()
    }
}

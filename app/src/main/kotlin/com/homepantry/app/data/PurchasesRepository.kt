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

    /** Alta masiva: todas las líneas confirmadas de un mismo ticket se escriben juntas. */
    suspend fun addPurchases(purchases: List<Purchase>) {
        if (purchases.isEmpty()) return
        val batch = firestore.batch()
        purchases.forEach { purchase -> batch.set(collection().document(), purchase) }
        batch.commit().await()
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

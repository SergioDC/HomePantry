package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Correcciones de nombre de producto de la casa, compartidas en tiempo real:
 * households/{householdCode}/productAliases
 */
class ProductAliasesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("productAliases")

    fun observeAliases(): Flow<List<ProductAlias>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(ProductAlias::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Escribe cada alias entero en su id (una corrección nueva sustituye a la anterior). */
    suspend fun saveAliases(aliases: List<ProductAlias>) {
        aliases.filter { it.id.isNotBlank() }.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { alias -> batch.set(collection().document(alias.id), alias) }
            batch.commit().await()
        }
    }
}

package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Entradas del menú: households/{householdCode}/mealEntries.
 * Las semanas se consultan por rango sobre el campo `date` ("yyyy-MM-dd"), que al ser un
 * único campo no necesita índice compuesto; el orden por franja se aplica en el cliente.
 */
class MealEntriesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("mealEntries")

    private fun rangeQuery(from: String, to: String) =
        collection().whereGreaterThanOrEqualTo("date", from).whereLessThanOrEqualTo("date", to)

    /** Emite las entradas de [from] a [to] (ambos incluidos) cada vez que cambia algo en ese rango. */
    fun observeRange(from: String, to: String): Flow<List<MealEntry>> = callbackFlow {
        val registration = rangeQuery(from, to).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(MealEntry::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Lectura única del rango, para duplicar semanas. */
    suspend fun getRange(from: String, to: String): List<MealEntry> =
        rangeQuery(from, to).get().await().documents.mapNotNull { it.toObject(MealEntry::class.java) }

    suspend fun addEntry(entry: MealEntry): String = collection().add(entry).await().id

    suspend fun updateEntry(entry: MealEntry) {
        collection().document(entry.id).set(entry).await()
    }

    suspend fun deleteEntry(id: String) {
        collection().document(id).delete().await()
    }

    /** Deshace un borrado: vuelve a escribir la entrada en su mismo documento. */
    suspend fun restoreEntry(entry: MealEntry) {
        collection().document(entry.id).set(entry).await()
    }

    /** Asigna [person] (null = sin asignar) a las entradas [ids], en lotes de como mucho 500 operaciones. */
    suspend fun updatePerson(ids: List<String>, person: String?) {
        ids.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.update(collection().document(id), "person", person) }
            batch.commit().await()
        }
    }

    /** Cuántas entradas del menú usan un plato (para avisar antes de borrarlo). */
    suspend fun countByDish(dishId: String): Int =
        collection().whereEqualTo("dishId", dishId).get().await().size()

    /**
     * Duplicado de semana: escribe las entradas nuevas y luego borra las indicadas, en lotes de
     * como mucho 500 operaciones. Se escribe primero: si algo falla a medias, quedan duplicados
     * pero no se pierde nada.
     */
    suspend fun applyBatch(toWrite: List<MealEntry>, toDelete: List<String>) {
        toWrite.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { entry -> batch.set(collection().document(), entry) }
            batch.commit().await()
        }
        toDelete.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.delete(collection().document(id)) }
            batch.commit().await()
        }
    }
}

package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Platos de la casa: households/{householdCode}/dishes. Mismo patrón que ItemsRepository. */
class DishesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("dishes")

    fun observeDishes(): Flow<List<Dish>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(Dish::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** @return el id generado por Firestore para el nuevo plato. */
    suspend fun addDish(dish: Dish): String = collection().add(dish).await().id

    /** Id nuevo para un plato que se va a crear con [addDishes], generado en el cliente sin tocar la red. */
    fun newId(): String = collection().document().id

    /** Crea los platos con el id que ya traen, en lotes de como mucho 500 operaciones. */
    suspend fun addDishes(dishes: List<Dish>) {
        dishes.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { dish -> batch.set(collection().document(dish.id), dish) }
            batch.commit().await()
        }
    }

    suspend fun updateDish(dish: Dish) {
        collection().document(dish.id).set(dish).await()
    }

    suspend fun deleteDish(id: String) {
        collection().document(id).delete().await()
    }
}

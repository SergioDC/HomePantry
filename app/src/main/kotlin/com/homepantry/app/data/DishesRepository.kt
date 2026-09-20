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

    suspend fun updateDish(dish: Dish) {
        collection().document(dish.id).set(dish).await()
    }

    suspend fun deleteDish(id: String) {
        collection().document(id).delete().await()
    }
}

package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Personas del menú y su color: households/{householdCode}/people, un documento por persona con
 * [personDocId] como id. Mismo patrón que DishesRepository. Las escrituras usan `merge` para que
 * crear a una persona no pise el color ya elegido y elegir color no dependa de que ya exista.
 */
class PeopleRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("people")

    fun observePeople(): Flow<List<Person>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(Person::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Crea el documento de [name] (null = la familia) si no existe, sin tocar su color. */
    suspend fun ensure(name: String?) {
        collection().document(personDocId(name))
            .set(mapOf("name" to (name ?: FAMILY_NAME)), SetOptions.merge())
            .await()
    }

    /** Guarda el color de [name] (null = la familia), creando el documento si hace falta. */
    suspend fun setColor(name: String?, color: PersonColor) {
        collection().document(personDocId(name))
            .set(mapOf("name" to (name ?: FAMILY_NAME), "color" to color.name), SetOptions.merge())
            .await()
    }
}

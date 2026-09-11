package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Registro mínimo de quién ha entrado en la casa: cada dispositivo actualiza su propio
 * documento (su uid anónimo de Firebase Auth) al arrancar, con su nombre y la fecha --
 * no hay borrado automático de miembros antiguos, solo constancia de quién ha estado.
 */
class MembersRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("members")

    fun observeMembers(): Flow<List<Member>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val members = snapshot?.documents?.mapNotNull { it.toObject(Member::class.java) } ?: emptyList()
            trySend(members)
        }
        awaitClose { registration.remove() }
    }

    suspend fun upsertSelf(uid: String, name: String) {
        collection().document(uid).set(Member(name = name)).await()
    }
}

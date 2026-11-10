package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import kotlinx.coroutines.tasks.await

/**
 * Token FCM de un móvil, para que el notificador del NAS (tools/notifier) le mande push.
 * households/{householdCode}/pushTokens/{uid}
 *
 * Va aparte de `members` porque [MembersRepository.upsertSelf] reescribe el documento del
 * miembro entero en cada arranque y borraría el token.
 */
data class PushToken(
    @DocumentId
    val id: String = "",
    val token: String = "",
    @ServerTimestamp
    val updatedAt: Date? = null
)

class PushTokensRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("pushTokens")

    /** Cada móvil escribe solo su documento; `set` entero es seguro porque nadie más lo toca. */
    suspend fun save(uid: String, token: String) {
        collection().document(uid).set(PushToken(token = token)).await()
    }
}

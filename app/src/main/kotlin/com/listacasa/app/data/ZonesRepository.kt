package com.listacasa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repositorio de zonas configurables (Nevera, Despensa, Congelador, Otros...).
 * TODO (Claude Code): al crear un household nuevo, sembrar Firestore con
 * DEFAULT_ZONE_NAMES (ver Zone.kt) para que la casa arranque con las 4 zonas base.
 */
class ZonesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("zones")

    fun observeZones(): Flow<List<Zone>> = callbackFlow {
        val registration = collection().orderBy("order").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val zones = snapshot?.documents?.mapNotNull { it.toObject(Zone::class.java) } ?: emptyList()
            trySend(zones)
        }
        awaitClose { registration.remove() }
    }

    suspend fun addZone(name: String, order: Int) {
        collection().add(Zone(name = name, order = order)).await()
    }

    suspend fun renameZone(zoneId: String, newName: String) {
        collection().document(zoneId).update("name", newName).await()
    }

    /** Eliminar zona. Reasignar productos a "Otros" debe hacerse ANTES desde
     *  ItemsRepository.reassignZone(), avisando al usuario cuántos productos afecta. */
    suspend fun deleteZone(zoneId: String) {
        collection().document(zoneId).delete().await()
    }

    suspend fun seedDefaultZones() {
        DEFAULT_ZONE_NAMES.forEachIndexed { index, name ->
            addZone(name, index)
        }
    }
}

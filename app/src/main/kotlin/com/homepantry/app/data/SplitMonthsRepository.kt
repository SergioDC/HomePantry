package com.homepantry.app.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Meses de reparto: households/{householdCode}/splitMonths. Cada mes tiene las subcolecciones
 * `expenses` y `photos`, que gestionan SplitExpensesRepository y SplitPhotosRepository.
 */
class SplitMonthsRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection() =
        firestore.collection("households").document(householdCode).collection("splitMonths")

    /** Meses ordenados por [SplitMonth.monthKey] descendente; se ordena en memoria para no necesitar índices. */
    fun observeMonths(): Flow<List<SplitMonth>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val months = snapshot?.documents?.mapNotNull { it.toObject(SplitMonth::class.java) } ?: emptyList()
            trySend(months.sortedWith(compareByDescending<SplitMonth> { it.monthKey }.thenByDescending { it.createdAt }))
        }
        awaitClose { registration.remove() }
    }

    suspend fun createMonth(name: String, monthKey: String, memberIds: List<String>, activity: Boolean): String =
        collection().add(SplitMonth(name = name, monthKey = monthKey, memberIds = memberIds, activity = activity)).await().id

    suspend fun rename(monthId: String, name: String) {
        collection().document(monthId).update("name", name).await()
    }

    /**
     * Borra el mes con sus gastos y fotos. Firestore no borra subcolecciones al borrar el
     * documento padre: quedarían huérfanas e invisibles, así que se borran a mano, en lotes de
     * 400. El documento del mes va al final para que un fallo a medias deje el mes visible y se
     * pueda reintentar.
     *
     * Los objetos de Storage se borran antes que nada con [deletePhotoObject] (el repositorio no
     * depende de Storage): la ruta solo está en el documento de la foto, y si se borrara primero
     * el documento, un fallo o un cierre de la app dejaría el objeto huérfano para siempre.
     * Un gasto o foto que otro miembro añada mientras se borra puede quedar huérfano; para una
     * app doméstica se acepta.
     */
    suspend fun delete(monthId: String, deletePhotoObject: suspend (storagePath: String) -> kotlin.Unit) {
        val month = collection().document(monthId)
        val expenses = month.collection("expenses").get().await().documents
        val photos = month.collection("photos").get().await().documents
        photos.mapNotNull { it.getString("storagePath")?.takeIf(String::isNotBlank) }
            .forEach { deletePhotoObject(it) }
        val refs: List<DocumentReference> = expenses.map { it.reference } + photos.map { it.reference }
        refs.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
        month.delete().await()
    }
}

package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Gastos de un mes de reparto: households/{householdCode}/splitMonths/{monthId}/expenses.
 * El mes va en cada llamada, no en el constructor, para que el ViewModel cambie de mes con
 * `flatMapLatest` sin recrear el repositorio.
 */
class SplitExpensesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun collection(monthId: String) =
        firestore.collection("households").document(householdCode)
            .collection("splitMonths").document(monthId).collection("expenses")

    /** Gastos del mes, el más reciente primero (orden en memoria, sin índices). */
    fun observeExpenses(monthId: String): Flow<List<SplitExpense>> = callbackFlow {
        val registration = collection(monthId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val expenses = snapshot?.documents?.mapNotNull { it.toObject(SplitExpense::class.java) } ?: emptyList()
            trySend(expenses.sortedWith(compareByDescending<SplitExpense> { it.date }.thenByDescending { it.createdAt }))
        }
        awaitClose { registration.remove() }
    }

    suspend fun add(monthId: String, expense: SplitExpense) {
        collection(monthId).add(expense).await()
    }

    /** Varios gastos de golpe (importación de cargos), en batches de 500, el límite de Firestore. */
    suspend fun addAll(monthId: String, expenses: List<SplitExpense>) {
        expenses.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            // Con id fijo, reintentar tras un fallo a medias (más de 500 gastos) sobrescribe los ya
            // escritos en vez de duplicarlos. Firestore no serializa el campo @DocumentId.
            chunk.forEach { expense ->
                val doc = if (expense.id.isBlank()) collection(monthId).document() else collection(monthId).document(expense.id)
                batch.set(doc, expense)
            }
            batch.commit().await()
        }
    }

    /**
     * Solo los campos editables, con `update` y no `set` del objeto entero: así no se pisa
     * `createdAt` ni lo que otro miembro haya cambiado a la vez en un campo que aquí no se toca.
     */
    suspend fun update(monthId: String, expense: SplitExpense) {
        collection(monthId).document(expense.id).update(
            mapOf(
                "title" to expense.title,
                "amountCents" to expense.amountCents,
                "payerId" to expense.payerId,
                "participants" to expense.participants,
                "date" to expense.date,
                "reimbursement" to expense.reimbursement,
                "emoji" to expense.emoji,
                // Un mapa vacío se escribe tal cual: al volver a «partes iguales» borra los importes anteriores.
                "shares" to expense.shares
            )
        ).await()
    }

    suspend fun delete(monthId: String, expenseId: String) {
        collection(monthId).document(expenseId).delete().await()
    }
}

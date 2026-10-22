package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Un gasto repartido a partes iguales entre [participants]. Un reembolso es un gasto con
 * [reimbursement] = true, pagador = quien devuelve y participantes = [receptor]: para los saldos
 * es un gasto más, y solo se excluye del total del mes.
 * households/{householdCode}/splitMonths/{monthId}/expenses/{expenseId}
 *
 * El campo se llama `reimbursement` y no `isReimbursement` porque Firestore quita el prefijo
 * `is` al mapear propiedades Kotlin y el nombre guardado no coincidiría con el leído.
 */
data class SplitExpense(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val amountCents: Long = 0L,
    val payerId: String = "",
    val participants: List<String> = emptyList(),
    val date: Date? = null,
    val reimbursement: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null
)

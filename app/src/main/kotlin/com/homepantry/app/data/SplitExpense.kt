package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Un gasto repartido entre [participants], a partes iguales o según [shares]. Un reembolso es un gasto con
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
    /** Emoji elegido para el gasto; vacío = icono automático según el concepto. */
    val emoji: String = "",
    /**
     * Céntimos que le tocan a cada participante cuando no se reparte a partes iguales. Vacío =
     * reparto igual entre [participants] (todos los gastos anteriores a este campo). Si no está
     * vacío, [participants] son sus claves con importe > 0, para que el código que solo mira la
     * lista siga siendo correcto. Leer siempre con [expenseShares].
     */
    val shares: Map<String, Long> = emptyMap(),
    @ServerTimestamp
    val createdAt: Date? = null
)

package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Un grupo de SNcount (la "lista" de Tricount): un mes o una actividad (un viaje, una cena).
 * La colección conserva el nombre `splitMonths` de cuando solo había meses.
 * households/{householdCode}/splitMonths/{monthId}
 *
 * El booleano se llama `activity` y no `isActivity` por la misma razón que
 * [SplitExpense.reimbursement]: Firestore quita el prefijo `is` al mapear.
 */
data class SplitMonth(
    @DocumentId
    val id: String = "",
    val name: String = "",
    /**
     * "yyyy-MM". En un mes, el mes al que pertenecen los gastos; en una actividad, el mes en que
     * se creó (solo para ordenar la lista). No es único.
     */
    val monthKey: String = "",
    /** true = actividad: sus gastos pueden tener cualquier fecha. */
    val activity: Boolean = false,
    /** Uids de los miembros que participan en este mes. */
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null
)

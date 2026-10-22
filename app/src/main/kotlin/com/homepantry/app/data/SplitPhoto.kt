package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Foto de un mes de reparto, suelta o ligada a un gasto.
 * households/{householdCode}/splitMonths/{monthId}/photos/{photoId}
 */
data class SplitPhoto(
    @DocumentId
    val id: String = "",
    /** URL de descarga, para pintarla con Coil. */
    val url: String = "",
    /** Ruta en Storage, necesaria para borrar el objeto (la URL no sirve para eso). */
    val storagePath: String = "",
    val note: String = "",
    val expenseId: String? = null,
    @ServerTimestamp
    val createdAt: Date? = null
)

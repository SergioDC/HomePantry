package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Línea de una compra registrada a partir de un ticket. Independiente de
 * `Item` (catálogo de compras separado del stock de despensa, ver spec
 * "Historial de compras a partir de foto de ticket").
 * households/{householdCode}/purchases/{purchaseId}
 */
data class Purchase(
    @DocumentId
    val id: String = "",
    val rawName: String = "",
    val normalizedName: String = "",
    val price: Double = 0.0,
    val date: Date = Date(),
    val addedBy: String? = null,
    val ticketPhotoUrl: String? = null
)

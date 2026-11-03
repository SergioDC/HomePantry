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
    val ticketPhotoUrl: String? = null,
    /** Cantidad comprada en la línea (en `unit`). `price` sigue siendo el total de la línea. */
    val quantity: Double = 1.0,
    /** Nombre de un valor de [Unit]: UD, KG o L. */
    val unit: String = Unit.UD.name,
    /** Supermercado de la compra, tal y como se muestra. Null = desconocido (compras antiguas). */
    val store: String? = null,
    /** Id del escaneo (ticket) al que pertenece la línea. Null en compras anteriores a los tickets. */
    val ticketId: String? = null,
    /**
     * Nombre genérico (tipo + variedad, sin marca ni tamaño: "Arándanos", "Leche entera"), que es
     * lo que se muestra y por lo que se agrupa. Vacío en compras antiguas sin normalizar: entonces
     * se usa `rawName`, como antes.
     */
    val productName: String = "",
    /** Cantidad de `packUnit` en una unidad del envase (0.3 para 300 g). Solo cuenta con `unit` UD. */
    val packSize: Double? = null,
    /** "KG" o "L": unidad de `packSize`. */
    val packUnit: String? = null
)

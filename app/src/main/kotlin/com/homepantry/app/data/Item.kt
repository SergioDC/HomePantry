package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class Unit(val label: String) {
    UD("unidad(es)"),
    KG("kg"),
    G("g"),
    L("litro(s)"),
    ML("ml"),
    PAQUETE("paquete(s)"),
    DOCENA("docena")
}

/**
 * Producto de la lista de compra/despensa.
 * Se sincroniza en tiempo real vía Firestore:
 * households/{householdCode}/items/{itemId}
 *
 * NOTA para Claude Code: el constructor sin argumentos es requerido por el
 * deserializador de Firestore (toObject<Item>()).
 */
data class Item(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val qty: Double = 1.0,
    val unit: String = Unit.UD.name,
    val note: String? = null,
    val zone: String = "",
    val photoUrl: String? = null,
    val barcode: String? = null,
    val done: Boolean = false,
    val addedBy: String? = null,
    @ServerTimestamp
    val addedAt: Date? = null
)

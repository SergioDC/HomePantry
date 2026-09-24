package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Ingrediente de un plato: texto libre, con cantidad y unidad opcionales (nombre de [Unit]). */
data class Ingredient(
    val name: String = "",
    val qty: Double? = null,
    val unit: String? = null
)

/**
 * Plato de la lista de la casa.
 * households/{householdCode}/dishes/{dishId}
 *
 * El constructor sin argumentos es requerido por el deserializador de Firestore.
 */
data class Dish(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val note: String? = null,
    val addedBy: String? = null,
    @ServerTimestamp
    val addedAt: Date? = null
)

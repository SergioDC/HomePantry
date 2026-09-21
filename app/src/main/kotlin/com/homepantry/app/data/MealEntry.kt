package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId

enum class MealSlot(val label: String) {
    BREAKFAST("Desayuno"),
    LUNCH("Comida"),
    DINNER("Cena")
}

/**
 * Una línea del menú: un plato (o texto libre) en una franja de un día.
 * households/{householdCode}/mealEntries/{entryId}
 *
 * `name` es una copia del nombre del plato al añadirlo: si el plato se borra o se
 * renombra, la entrada sigue mostrándose. `dishId == null` (o un id que ya no existe)
 * significa texto libre. `date` es "yyyy-MM-dd" para poder consultar una semana por rango.
 * `person` es a quién va la entrada (un nombre libre, p. ej. el niño del menú del comedor); `null`
 * significa toda la familia, así que las entradas anteriores a este campo siguen valiendo.
 */
data class MealEntry(
    @DocumentId
    val id: String = "",
    val date: String = "",
    val slot: String = MealSlot.LUNCH.name,
    val dishId: String? = null,
    val name: String = "",
    val order: Int = 0,
    val addedBy: String? = null,
    val person: String? = null
)

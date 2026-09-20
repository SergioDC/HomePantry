package com.homepantry.app.data

import java.time.LocalDate

enum class DuplicateMode { REPLACE, MERGE }

/** Qué escribir (entradas nuevas, sin id) y qué borrar (ids) al duplicar una semana. */
data class DuplicatePlan(val toWrite: List<MealEntry>, val toDelete: List<String>)

/**
 * Plan para copiar la semana [fromWeekStart] a la [toWeekStart].
 * - REPLACE: borra las entradas del destino y escribe las copias.
 * - MERGE: no borra nada; las copias van detrás de lo que ya haya en cada franja del destino.
 */
fun planDuplicateWeek(
    source: List<MealEntry>,
    target: List<MealEntry>,
    fromWeekStart: LocalDate,
    toWeekStart: LocalDate,
    mode: DuplicateMode
): DuplicatePlan {
    val copies = shiftEntries(source, fromWeekStart, toWeekStart)
    return when (mode) {
        DuplicateMode.REPLACE -> DuplicatePlan(toWrite = copies, toDelete = target.map { it.id })
        DuplicateMode.MERGE -> {
            val highestOrder = target.groupBy { it.date to it.slot }.mapValues { (_, list) -> list.maxOf { it.order } }
            val shifted = copies.map { copy ->
                val base = highestOrder[copy.date to copy.slot]
                if (base == null) copy else copy.copy(order = copy.order + base + 1)
            }
            DuplicatePlan(toWrite = shifted, toDelete = emptyList())
        }
    }
}

/** Orden para una entrada nueva al final de la franja. */
fun nextEntryOrder(entries: List<MealEntry>, date: String, slot: MealSlot): Int =
    (entries.filter { it.date == date && it.slot == slot.name }.maxOfOrNull { it.order } ?: -1) + 1

fun entriesFor(entries: List<MealEntry>, date: String, slot: MealSlot): List<MealEntry> =
    entries.filter { it.date == date && it.slot == slot.name }.sortedBy { it.order }

/** El plato cuyo nombre normalizado coincide exactamente con el texto escrito, o null (texto libre). */
fun matchDish(text: String, dishes: List<Dish>): Dish? {
    val key = normalizeProductName(text)
    if (key.isEmpty()) return null
    return dishes.firstOrNull { normalizeProductName(it.name) == key }
}

/** Platos cuyo nombre contiene el texto (sin acentos ni mayúsculas), por nombre; con texto en blanco, todos. */
fun suggestDishes(text: String, dishes: List<Dish>, limit: Int = 5): List<Dish> {
    val key = normalizeProductName(text)
    return dishes
        .filter { key.isEmpty() || normalizeProductName(it.name).contains(key) }
        .sortedBy { it.name.lowercase() }
        .take(limit)
}

/** Id del plato de la entrada solo si ese plato sigue existiendo; si no, se trata como texto libre. */
fun resolvedDishId(entry: MealEntry, dishes: List<Dish>): String? =
    entry.dishId?.takeIf { id -> dishes.any { it.id == id } }

package com.homepantry.app.data

import java.text.Normalizer
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

// ---- Personas (SPEC 2026-09-21: menú asignado a una persona) ----

private const val FAMILY_KEY = "familia"
const val MAX_PERSON_LENGTH = 30

/**
 * Clave para comparar nombres de persona: sin mayúsculas, acentos ni espacios de más. A diferencia
 * de [normalizeProductName] no quita la «s» final: Marco y Marcos son personas distintas.
 */
internal fun personKey(name: String): String =
    Normalizer.normalize(name.trim().lowercase().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** Las entradas de una franja que van a la misma persona; `person == null` es toda la familia. */
data class PersonGroup(val person: String?, val entries: List<MealEntry>)

/**
 * Agrupa las entradas por persona para mostrar su nombre una sola vez: primero la familia y luego
 * cada persona por orden de aparición. Nombres que solo difieren en mayúsculas o acentos son la
 * misma persona y se muestran con la primera grafía. Dentro de un grupo se conserva el orden.
 */
fun groupByPerson(entries: List<MealEntry>): List<PersonGroup> {
    val groups = mutableListOf<PersonGroup>()
    val family = entries.filter { it.person.isNullOrBlank() }
    if (family.isNotEmpty()) groups += PersonGroup(null, family)
    entries.filter { !it.person.isNullOrBlank() }
        .groupBy { personKey(it.person!!) }
        .values
        .forEach { list -> groups += PersonGroup(list.first().person!!.trim(), list) }
    return groups
}

/**
 * Persona a guardar a partir de lo que escribió el usuario: en blanco o «familia» es toda la
 * familia (null); si coincide con un nombre de [known] (sin distinguir mayúsculas ni acentos) se
 * reutiliza su grafía, para no acabar con «Pepe» y «pepe»; si no, se limpia y se pone mayúscula inicial.
 */
fun normalizePerson(input: String, known: List<String>): String? {
    val cleaned = input.trim().replace(Regex("\\s+"), " ").take(MAX_PERSON_LENGTH).trimEnd()
    val key = personKey(cleaned)
    if (key.isEmpty() || key == FAMILY_KEY) return null
    return known.firstOrNull { personKey(it) == key } ?: cleaned.replaceFirstChar { it.uppercase() }
}

/** Personas ya usadas en [entries], sin repetir (primera grafía) y ordenadas, para ofrecerlas como atajo. */
fun knownPeople(entries: List<MealEntry>): List<String> =
    entries.mapNotNull { it.person?.trim()?.takeIf { name -> name.isNotEmpty() } }
        .distinctBy { personKey(it) }
        .sortedBy { personKey(it) }

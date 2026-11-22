package com.homepantry.app.data

import java.time.LocalDate

/** Persona que representa a toda la casa en el menú (ver [MealEntry.person]). */
private const val FAMILY_PERSON = "Familia"

/** Una línea del menú de hoy en el panel de cocina; [person] es null cuando es para toda la familia. */
data class MenuLine(val person: String? = null, val name: String = "")

/** Las líneas de una franja (comida, cena…) del menú de hoy. */
data class MenuSlotSection(val slot: MealSlot = MealSlot.LUNCH, val lines: List<MenuLine> = emptyList())

/**
 * Menú de [today] para el panel: franjas en su orden (desayuno solo si lo hay) y, dentro, primero
 * lo de la familia y después lo de cada persona (p. ej. el comedor), para leerlo de un vistazo.
 */
fun todayMenuSections(entries: List<MealEntry>, today: LocalDate): List<MenuSlotSection> {
    val todays = entries.filter { it.date == today.toString() }
    return MealSlot.entries.mapNotNull { slot ->
        val inSlot = todays.filter { it.slot == slot.name }
        if (inSlot.isEmpty()) return@mapNotNull null
        val (family, others) = inSlot.partition { it.person == null || it.person.equals(FAMILY_PERSON, ignoreCase = true) }
        val lines = family.sortedBy { it.order }.map { MenuLine(null, it.name) } +
            others.sortedWith(compareBy<MealEntry>({ normalizeRecipeText(it.person.orEmpty()) }, { it.order }))
                .map { MenuLine(it.person, it.name) }
        MenuSlotSection(slot, lines)
    }
}

/** Productos en casa de una zona raíz para «Se ha acabado…»; [zoneId] null agrupa los de zonas que ya no existen. */
data class RestockSection(val zoneId: String? = null, val zoneName: String = "", val items: List<Item> = emptyList())

/**
 * Lo que hay en casa (`done = true`) agrupado por zona raíz en el orden de la app, con lo de las
 * subzonas dentro de su raíz y lo de zonas borradas al final, en [noZoneName], para que nada
 * desaparezca. [query] filtra por palabras, sin tildes ni mayúsculas.
 */
fun restockSections(items: List<Item>, zones: List<Zone>, query: String, noZoneName: String): List<RestockSection> {
    val words = normalizeRecipeText(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val byId = zones.associateBy { it.id }
    fun rootOf(zoneId: String): Zone? {
        var zone = byId[zoneId] ?: return null
        val seen = mutableSetOf(zone.id)
        while (true) {
            val parent = zone.parentZoneId?.let { byId[it] } ?: return zone
            // Un ciclo en los datos no debe colgar el panel.
            if (!seen.add(parent.id)) return zone
            zone = parent
        }
    }
    val inHouse = items.filter { item ->
        item.done && normalizeRecipeText(item.name).let { name -> words.all { it in name } }
    }
    val grouped = inHouse.groupBy { rootOf(it.zone)?.id }
    val sortItems = { list: List<Item> -> list.sortedBy { normalizeRecipeText(it.name) } }
    val sections = zones.filter { it.parentZoneId == null }.sortedBy { it.order }.mapNotNull { zone ->
        grouped[zone.id]?.let { RestockSection(zone.id, zone.name, sortItems(it)) }
    }
    // Una raíz que sea subzona de una zona borrada sale como raíz: rootOf la devuelve, pero no está en la lista de raíces.
    val orphans = grouped.filterKeys { key -> key == null || sections.none { it.zoneId == key } }.values.flatten()
    return if (orphans.isEmpty()) sections else sections + RestockSection(null, noZoneName, sortItems(orphans))
}

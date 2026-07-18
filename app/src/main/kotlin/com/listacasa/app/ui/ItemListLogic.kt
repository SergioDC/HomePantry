package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone

data class ZoneSection(val zone: Zone, val items: List<Item>)

enum class SortMode { NEWEST_FIRST, OLDEST_FIRST, ALPHABETICAL }

/** "X de Y listos" — comprados vs total (SPEC.md sec 1.4). */
fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
}

/**
 * Agrupa por zona (orden de zona) y dentro de cada zona ordena pendientes
 * primero y luego comprados (SPEC.md sec 1.4). `filterZoneId` == "ALL" (o null)
 * no filtra; cualquier otro valor limita el resultado a esa zona. `sortMode`
 * decide el orden dentro de cada subgrupo pendiente/comprado (cierre de
 * huecos §10).
 */
fun groupAndSort(
    items: List<Item>,
    zones: List<Zone>,
    filterZoneId: String?,
    sortMode: SortMode = SortMode.NEWEST_FIRST
): List<ZoneSection> {
    val isFiltered = filterZoneId != null && filterZoneId != "ALL"
    val relevantZones = zones.sortedBy { it.order }
        .filter { !isFiltered || it.id == filterZoneId }

    val withinGroupComparator: Comparator<Item> = when (sortMode) {
        SortMode.NEWEST_FIRST -> compareByDescending { it.addedAt }
        SortMode.OLDEST_FIRST -> compareBy { it.addedAt }
        SortMode.ALPHABETICAL -> compareBy { it.name.lowercase() }
    }

    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }
            .sortedWith(compareBy<Item> { it.done }.then(withinGroupComparator))
        if (zoneItems.isEmpty() && !isFiltered) null else ZoneSection(zone, zoneItems)
    }
}

/** Filtro de búsqueda por texto, client-side (cierre de huecos §11). */
fun filterItemsByQuery(items: List<Item>, query: String): List<Item> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return items
    return items.filter { it.name.lowercase().contains(normalized) }
}

/**
 * Busca un producto pendiente con el mismo código de barras, para ofrecer
 * sumar cantidad en vez de crear un duplicado (cierre de huecos §3).
 */
fun findPendingDuplicateByBarcode(items: List<Item>, barcode: String): Item? =
    items.firstOrNull { it.barcode == barcode && !it.done }

package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone

data class ZoneSection(val zone: Zone, val items: List<Item>)

/** "X de Y listos" — comprados vs total (SPEC.md sec 1.4). */
fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
}

/**
 * Agrupa por zona (orden de zona) y dentro de cada zona ordena pendientes
 * primero y luego comprados (SPEC.md sec 1.4). `filterZoneId` == "ALL" (o null)
 * no filtra; cualquier otro valor limita el resultado a esa zona.
 */
fun groupAndSort(items: List<Item>, zones: List<Zone>, filterZoneId: String?): List<ZoneSection> {
    val isFiltered = filterZoneId != null && filterZoneId != "ALL"
    val relevantZones = zones.sortedBy { it.order }
        .filter { !isFiltered || it.id == filterZoneId }

    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }
            .sortedWith(compareBy({ it.done }, { it.addedAt }))
        if (zoneItems.isEmpty() && !isFiltered) null else ZoneSection(zone, zoneItems)
    }
}

package com.homepantry.app.ui

import com.homepantry.app.data.Item
import com.homepantry.app.data.Zone
import com.homepantry.app.data.resolvedZoneColor
import com.homepantry.app.data.subzonesOf
import com.homepantry.app.data.zoneDisplayLabel

data class ZoneSection(val zone: Zone, val items: List<Item>)

enum class SortMode { NEWEST_FIRST, OLDEST_FIRST, ALPHABETICAL }

/** Vista Lista: agrupada por zona (hoy) o plana (Nocturne, cierre de huecos §Nocturne). */
enum class ListViewMode { GROUPED, FLAT }

data class FlatRow(val item: Item, val zoneName: String, val zoneColor: String? = null)

/** "X de Y listos" — comprados vs total (SPEC.md sec 1.4). */
fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
}

private fun withinGroupComparator(sortMode: SortMode): Comparator<Item> = when (sortMode) {
    SortMode.NEWEST_FIRST -> compareByDescending { it.addedAt }
    SortMode.OLDEST_FIRST -> compareBy { it.addedAt }
    SortMode.ALPHABETICAL -> compareBy { it.name.lowercase() }
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

    val comparator = compareBy<Item> { it.done }.then(withinGroupComparator(sortMode))

    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }.sortedWith(comparator)
        if (zoneItems.isEmpty() && !isFiltered) null else ZoneSection(zone, zoneItems)
    }
}

/**
 * Igual que `groupAndSort` pero sin agrupar: una única lista con pendientes
 * antes que comprados, cada fila con el nombre de su zona para el chip de
 * etiqueta (pantalla "Todo" de Nocturne).
 */
fun flattenAndSort(
    items: List<Item>,
    zones: List<Zone>,
    sortMode: SortMode = SortMode.NEWEST_FIRST
): List<FlatRow> {
    val orderedZones = zones.sortedBy { it.order }
    val zoneNameById = orderedZones.associate { it.id to zoneDisplayLabel(it, zones) }
    val zoneColorById = orderedZones.withIndex().associate { (index, zone) -> zone.id to resolvedZoneColor(zone, index) }
    val comparator = compareBy<Item> { it.done }.then(withinGroupComparator(sortMode))
    return items.sortedWith(comparator).map {
        FlatRow(item = it, zoneName = zoneNameById[it.zone] ?: "", zoneColor = zoneColorById[it.zone])
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

data class ZoneSummary(val zone: Zone, val itemCount: Int, val pendingCount: Int)

/**
 * Resumen por zona para las tarjetas del dashboard "Almacén" (Nocturne). `itemCount`
 * cuenta solo lo que tienes (done=true) -- lo pendiente de esa zona ya no se ve dentro
 * de ZoneDetailScreen (vive en Lista de la compra), así que el total debe coincidir con
 * lo que realmente se ve al entrar en la zona. Solo hay tarjeta por zona raíz -- las
 * subzonas no tienen tarjeta propia, pero sus productos cuentan dentro de la de su padre.
 */
fun zoneSummaries(items: List<Item>, zones: List<Zone>): List<ZoneSummary> {
    val rootZones = zones.filter { it.parentZoneId == null }.sortedBy { it.order }
    return rootZones.map { zone ->
        val zoneIds = setOf(zone.id) + subzonesOf(zone.id, zones).map { it.id }
        val zoneItems = items.filter { it.zone in zoneIds }
        ZoneSummary(zone = zone, itemCount = zoneItems.count { it.done }, pendingCount = zoneItems.count { !it.done })
    }
}

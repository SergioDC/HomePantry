package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId

/**
 * Zona de la despensa/casa donde se guarda o usa un producto
 * (ej. Nevera, Congelador, Despensa, Otros). Configurable por el usuario:
 * crear, renombrar, eliminar (con reasignación de productos a "Otros").
 *
 * households/{householdCode}/zones/{zoneId}
 */
data class Zone(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    /** Color elegido por el usuario (hex "#RRGGBB"). Null = sin personalizar, usar zoneColorFor(index). */
    val color: String? = null,
    /** Id de la zona padre si esta es una subzona; null si es una zona raíz. Un solo nivel de anidación. */
    val parentZoneId: String? = null
)

val DEFAULT_ZONE_NAMES = listOf("Nevera", "Congelador", "Despensa", "Otros")

val ZONE_COLORS = listOf(
    "#6C5CE7", "#FF7F6B", "#00B37D", "#F5A623",
    "#3D9CD8", "#E8628C", "#8A8DBF"
)

fun zoneColorFor(index: Int): String = ZONE_COLORS[index % ZONE_COLORS.size]

/** Color a mostrar para una zona: el elegido por el usuario, o el derivado de su posición si no personalizó ninguno. */
fun resolvedZoneColor(zone: Zone, index: Int): String = zone.color?.takeIf { it.isNotBlank() } ?: zoneColorFor(index)

/**
 * Color de la paleta para una zona nueva: el menos usado entre las zonas existentes
 * (el hex se compara sin distinguir mayúsculas); en un empate, el primero de la paleta.
 */
fun nextZoneColor(zones: List<Zone>): String {
    val usage = zones.mapNotNull { it.color?.trim()?.uppercase()?.takeIf { hex -> hex.isNotEmpty() } }
        .groupingBy { it }
        .eachCount()
    return ZONE_COLORS.minByOrNull { usage[it.uppercase()] ?: 0 } ?: ZONE_COLORS.first()
}

/**
 * Colores a fijar en zonas que aún no tienen ninguno (zonas anteriores a los colores fijos):
 * las raíces reciben el color que Almacén ya mostraba (por su posición entre las raíces) y
 * cada subzona recibe uno propio de la paleta, en un orden estable (por el `order` de su
 * zona padre y luego el suyo; las huérfanas al final). Devuelve id de zona a hex, solo para
 * las zonas que lo necesitan.
 */
fun zoneColorBackfill(zones: List<Zone>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val roots = zones.filter { it.parentZoneId == null }.sortedBy { it.order }
    roots.forEachIndexed { index, zone ->
        if (zone.color.isNullOrBlank()) result[zone.id] = zoneColorFor(index)
    }

    val rootPosition = roots.withIndex().associate { (index, zone) -> zone.id to index }
    val working = zones.map { zone -> zone.copy(color = result[zone.id] ?: zone.color) }.toMutableList()
    zones.filter { it.parentZoneId != null && it.color.isNullOrBlank() }
        .sortedWith(
            compareBy<Zone>(
                { zone -> zone.parentZoneId?.let { parentId -> rootPosition[parentId] } ?: Int.MAX_VALUE },
                { zone -> zone.order }
            )
        )
        .forEach { subzone ->
            val color = nextZoneColor(working)
            result[subzone.id] = color
            val position = working.indexOfFirst { it.id == subzone.id }
            working[position] = working[position].copy(color = color)
        }
    return result
}

const val PROTECTED_ZONE_NAME = "Otros"

/** "Otros" es la zona de reserva de reasignación y no se puede renombrar/eliminar (cierre de huecos §4). */
fun isProtectedZone(zone: Zone): Boolean = zone.name.equals(PROTECTED_ZONE_NAME, ignoreCase = true)

/** Icono auto-derivado: primera letra del nombre en mayúscula (Nocturne redesign, sin campo de icono editable). */
fun zoneIconLetter(zone: Zone): String = zone.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** Subzonas directas de una zona raíz (un solo nivel de anidación). */
fun subzonesOf(zoneId: String, zones: List<Zone>): List<Zone> =
    zones.filter { it.parentZoneId == zoneId }.sortedBy { it.order }

/** Etiqueta para mostrar una zona fuera de su propia pantalla de detalle: "Padre > Hija" para subzonas. */
fun zoneDisplayLabel(zone: Zone, zones: List<Zone>): String {
    val parent = zone.parentZoneId?.let { parentId -> zones.firstOrNull { it.id == parentId } }
    return if (parent != null) "${parent.name} > ${zone.name}" else zone.name
}

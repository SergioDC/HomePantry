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
    val order: Int = 0
)

val DEFAULT_ZONE_NAMES = listOf("Nevera", "Congelador", "Despensa", "Otros")

val ZONE_COLORS = listOf(
    "#6C5CE7", "#FF7F6B", "#00B37D", "#F5A623",
    "#3D9CD8", "#E8628C", "#8A8DBF"
)

fun zoneColorFor(index: Int): String = ZONE_COLORS[index % ZONE_COLORS.size]

const val PROTECTED_ZONE_NAME = "Otros"

/** "Otros" es la zona de reserva de reasignación y no se puede renombrar/eliminar (cierre de huecos §4). */
fun isProtectedZone(zone: Zone): Boolean = zone.name.equals(PROTECTED_ZONE_NAME, ignoreCase = true)

/** Icono auto-derivado: primera letra del nombre en mayúscula (Nocturne redesign, sin campo de icono editable). */
fun zoneIconLetter(zone: Zone): String = zone.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

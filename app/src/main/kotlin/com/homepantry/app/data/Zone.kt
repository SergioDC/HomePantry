package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import kotlin.math.abs
import kotlin.math.roundToInt

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

/** Fracción de luminosidad que le falta al color de la zona (hasta el blanco) que se le añade para el fondo de su tarjeta. */
const val ZONE_TINT_LIGHTEN_FACTOR = 0.8f

private val HEX_COLOR = Regex("^#([0-9A-Fa-f]{6})$")

/**
 * Versión más clara del color de una zona (hex "#RRGGBB"), para el fondo de su tarjeta: mismo
 * tono y saturación (HSL) y luminosidad L' = L + (1 - L) * factor, así que siempre es más clara
 * que el original. Devuelve null si el hex no es válido.
 */
fun lightenedZoneColor(hex: String, factor: Float = ZONE_TINT_LIGHTEN_FACTOR): String? {
    val rgb = HEX_COLOR.matchEntire(hex.trim())?.groupValues?.get(1)?.toInt(16) ?: return null
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }

    val newLightness = lightness + (1f - lightness) * factor
    val chroma = (1f - abs(2f * newLightness - 1f)) * saturation
    val x = chroma * (1f - abs((hue / 60f).mod(2f) - 1f))
    val m = newLightness - chroma / 2f
    val (r1, g1, b1) = when ((hue / 60f).toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(channel(r1), channel(g1), channel(b1))
}

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

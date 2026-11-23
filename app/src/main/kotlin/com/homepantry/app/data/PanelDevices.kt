package com.homepantry.app.data

/**
 * Dispositivos del panel de la cocina (diseño acordado para la #4). Con Home Assistant, cada
 * entidad con la etiqueta «Panel cocina» será uno de estos: el tipo sale del dominio de HA
 * (`light`, `cover`, `switch`) y el área, de su área de HA. Hasta entonces el panel usa una
 * muestra local que no controla nada.
 */
enum class DeviceKind { LIGHT, COVER, SWITCH }

/** Colores que se ofrecen a las luces que los admiten. */
enum class LightColor { WARM, COOL, PINK, BLUE, GREEN }

/** Escenas del panel; con Home Assistant serán sus entidades `scene`. */
enum class PanelScene { COOK, DINNER, NIGHT }

data class PanelDevice(
    val id: String = "",
    val areaId: String = "",
    val kind: DeviceKind = DeviceKind.SWITCH,
    val name: String = "",
    val on: Boolean = false,
    /** Brillo de 1 a 100 (luces). */
    val brightness: Int = 100,
    val color: LightColor = LightColor.WARM,
    /** Las luces que solo cambian de brillo no enseñan colores. */
    val supportsColor: Boolean = false,
    /** Posición de la persiana: 0 cerrada, 100 abierta. */
    val position: Int = 0,
    /** Adónde va la persiana mientras se mueve; null si está parada. */
    val target: Int? = null
)

/** Encendida, o en las persianas, abierta aunque sea un poco. */
fun PanelDevice.isActive(): Boolean = if (kind == DeviceKind.COVER) position > 0 else on

/** Agrupa por área en el orden en que aparece cada una. */
fun groupByArea(devices: List<PanelDevice>): List<Pair<String, List<PanelDevice>>> =
    devices.groupBy { it.areaId }.toList()

fun List<PanelDevice>.update(id: String, change: (PanelDevice) -> PanelDevice): List<PanelDevice> =
    map { if (it.id == id) change(it) else it }

/** Acción rápida del icono: encender o apagar; la persiana, subir del todo si está cerrada y si no, bajar. */
fun PanelDevice.quickAction(): PanelDevice = when (kind) {
    DeviceKind.COVER -> copy(target = if (position > 0) 0 else 100)
    else -> copy(on = !on)
}

/** «Apagar todo»: luces y enchufes; las persianas no se mueven. */
fun allOff(devices: List<PanelDevice>): List<PanelDevice> =
    devices.map { if (it.kind == DeviceKind.COVER) it else it.copy(on = false) }

fun applyScene(devices: List<PanelDevice>, scene: PanelScene, kitchenAreaId: String): List<PanelDevice> =
    devices.map { d ->
        when (scene) {
            PanelScene.COOK -> when {
                d.areaId != kitchenAreaId -> d
                d.kind == DeviceKind.LIGHT -> d.copy(on = true, brightness = 100)
                d.kind == DeviceKind.COVER -> d.copy(target = 100)
                else -> d
            }
            PanelScene.DINNER ->
                if (d.kind == DeviceKind.LIGHT) {
                    d.copy(on = true, brightness = 35, color = if (d.supportsColor) LightColor.WARM else d.color)
                } else {
                    d
                }
            PanelScene.NIGHT ->
                if (d.kind == DeviceKind.COVER) d.copy(target = 0) else d.copy(on = false)
        }
    }

/** Un paso del motor de las persianas: se acercan a su destino y se paran al llegar. */
fun stepCovers(devices: List<PanelDevice>, step: Int = 2): List<PanelDevice> =
    devices.map { d ->
        val target = d.target ?: return@map d
        val next = when {
            d.position < target -> minOf(d.position + step, target)
            d.position > target -> maxOf(d.position - step, target)
            else -> target
        }
        d.copy(position = next, target = if (next == target) null else target)
    }

fun anyCoverMoving(devices: List<PanelDevice>): Boolean = devices.any { it.target != null }

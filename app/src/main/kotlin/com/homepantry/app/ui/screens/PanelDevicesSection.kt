package com.homepantry.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepantry.app.R
import com.homepantry.app.data.DeviceKind
import com.homepantry.app.data.LightColor
import com.homepantry.app.data.PanelDevice
import com.homepantry.app.data.PanelScene
import com.homepantry.app.data.allOff
import com.homepantry.app.data.anyCoverMoving
import com.homepantry.app.data.applyScene
import com.homepantry.app.data.groupByArea
import com.homepantry.app.data.isActive
import com.homepantry.app.data.quickAction
import com.homepantry.app.data.stepCovers
import com.homepantry.app.data.update
import com.homepantry.app.ui.theme.NocturneSurface
import kotlinx.coroutines.delay

private const val KITCHEN = "cocina"
private const val LIVING = "salon"

private val DarkInk = Color(0xFF161826)
private val TileOff = NocturneSurface.copy(alpha = 0.85f)
private val IconOff = Color(0xFF34364A)
private val Sky = Color(0xFF8FD3FF)
private val SwitchColors = listOf(Color(0xFF3DD6C6), Color(0xFFFF8A4C), Color(0xFFC9A2FF))

private fun LightColor.value(): Color = when (this) {
    LightColor.WARM -> Color(0xFFFFC46B)
    LightColor.COOL -> Color(0xFFE6EEFF)
    LightColor.PINK -> Color(0xFFFF7AB8)
    LightColor.BLUE -> Color(0xFF62B4FF)
    LightColor.GREEN -> Color(0xFF66E0A3)
}

private fun LightColor.label(): Int = when (this) {
    LightColor.WARM -> R.string.panel_devices_color_warm
    LightColor.COOL -> R.string.panel_devices_color_cool
    LightColor.PINK -> R.string.panel_devices_color_pink
    LightColor.BLUE -> R.string.panel_devices_color_blue
    LightColor.GREEN -> R.string.panel_devices_color_green
}

/** Muestra local hasta que exista Home Assistant (#4): no controla ningún aparato. */
private fun sampleDevices(context: Context): List<PanelDevice> = listOf(
    PanelDevice("luz_cocina", KITCHEN, DeviceKind.LIGHT, context.getString(R.string.panel_devices_sample_kitchen_light), on = true, brightness = 80, color = LightColor.COOL),
    PanelDevice("persiana_cocina", KITCHEN, DeviceKind.COVER, context.getString(R.string.panel_devices_sample_kitchen_blind), position = 100),
    PanelDevice("cafetera", KITCHEN, DeviceKind.SWITCH, context.getString(R.string.panel_devices_sample_coffee), on = true),
    PanelDevice("lampara_salon", LIVING, DeviceKind.LIGHT, context.getString(R.string.panel_devices_sample_living_lamp), on = true, brightness = 60, supportsColor = true),
    PanelDevice("tira_salon", LIVING, DeviceKind.LIGHT, context.getString(R.string.panel_devices_sample_led_strip), brightness = 50, color = LightColor.PINK, supportsColor = true),
    PanelDevice("persiana_salon", LIVING, DeviceKind.COVER, context.getString(R.string.panel_devices_sample_living_blind), position = 40),
    PanelDevice("calefactor", LIVING, DeviceKind.SWITCH, context.getString(R.string.panel_devices_sample_heater))
)

private fun areaLabel(areaId: String): Int = if (areaId == KITCHEN) R.string.panel_devices_area_kitchen else R.string.panel_devices_area_living

private fun kindIcon(kind: DeviceKind): ImageVector = when (kind) {
    DeviceKind.LIGHT -> Icons.Filled.Lightbulb
    DeviceKind.COVER -> Icons.Filled.Blinds
    DeviceKind.SWITCH -> Icons.Filled.Power
}

@Composable
private fun statusText(d: PanelDevice): String = when (d.kind) {
    DeviceKind.LIGHT -> if (d.on) stringResource(R.string.panel_devices_light_on, d.brightness) else stringResource(R.string.panel_devices_light_off)
    DeviceKind.SWITCH -> stringResource(if (d.on) R.string.panel_devices_switch_on else R.string.panel_devices_switch_off)
    DeviceKind.COVER -> when {
        d.target != null && d.target > d.position -> stringResource(R.string.panel_devices_cover_up)
        d.target != null && d.target < d.position -> stringResource(R.string.panel_devices_cover_down)
        d.position == 0 -> stringResource(R.string.panel_devices_cover_closed)
        d.position == 100 -> stringResource(R.string.panel_devices_cover_open)
        else -> stringResource(R.string.panel_devices_cover_partial, d.position)
    }
}

/**
 * Estado de los dispositivos del panel. Se guarda en [PanelScreen] porque el detalle se dibuja
 * encima de todo el panel, como una capa y no como un `Dialog`: los toques de una ventana aparte
 * no llegan a [com.homepantry.app.ui.PanelIdleHost] y el panel se atenuaría con alguien usándolo.
 */
@Stable
class PanelDevicesState(initial: List<PanelDevice>) {
    var devices by mutableStateOf(initial)
    var scene by mutableStateOf<PanelScene?>(null)
    var openId by mutableStateOf<String?>(null)

    fun change(id: String, transform: (PanelDevice) -> PanelDevice) {
        scene = null
        devices = devices.update(id, transform)
    }

    fun accent(d: PanelDevice): Color = when (d.kind) {
        DeviceKind.LIGHT -> d.color.value()
        DeviceKind.COVER -> Sky
        DeviceKind.SWITCH -> {
            val i = devices.filter { it.kind == DeviceKind.SWITCH }.indexOfFirst { it.id == d.id }
            SwitchColors[i.coerceAtLeast(0) % SwitchColors.size]
        }
    }
}

@Composable
fun rememberPanelDevicesState(): PanelDevicesState {
    val context = LocalContext.current
    val state = remember { PanelDevicesState(sampleDevices(context)) }
    val moving = anyCoverMoving(state.devices)
    // Las persianas se mueven poco a poco, como las de verdad, y se pueden parar a medio camino.
    LaunchedEffect(moving) {
        while (moving && anyCoverMoving(state.devices)) {
            delay(60)
            state.devices = stepCovers(state.devices)
        }
    }
    return state
}

/**
 * Dispositivos del panel, agrupados por área en mosaicos de tres por fila (diseño de la #4).
 * Tocar el icono hace la acción rápida; tocar el nombre abre el detalle (los enchufes no tienen
 * detalle: se encienden y apagan). Con [fill] la lista de áreas ocupa el alto y se desplaza.
 */
@Composable
fun PanelDevicesSection(state: PanelDevicesState, modifier: Modifier, fill: Boolean) {
    val devices = state.devices
    val scene = state.scene
    fun accent(d: PanelDevice) = state.accent(d)
    fun change(id: String, transform: (PanelDevice) -> PanelDevice) = state.change(id, transform)

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.panel_devices_title), style = OnSky, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.panel_devices_sample), color = SectionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val onCount = devices.count { it.kind != DeviceKind.COVER && it.on }
            Text(pluralStringResource(R.plurals.panel_devices_on_count, onCount, onCount), style = OnSky.copy(color = OnSkySoft), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { state.scene = null; state.devices = allOff(state.devices) },
                shape = RoundedCornerShape(22.dp),
                color = TileOff,
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.height(44.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.panel_devices_all_off), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            (if (fill) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            groupByArea(devices).forEach { (areaId, areaDevices) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(areaLabel(areaId)).uppercase(),
                        style = OnSky.copy(color = SectionLabel),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    areaDevices.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { d ->
                                DeviceTile(
                                    d,
                                    accent(d),
                                    Modifier.weight(1f),
                                    onQuick = { change(d.id) { it.quickAction() } },
                                    onOpen = {
                                        if (d.kind == DeviceKind.SWITCH) change(d.id) { it.copy(on = !it.on) } else state.openId = d.id
                                    }
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(PanelScene.COOK, R.string.panel_devices_scene_cook, Color(0xFFE6EEFF)),
                Triple(PanelScene.DINNER, R.string.panel_devices_scene_dinner, Color(0xFFFFC46B)),
                Triple(PanelScene.NIGHT, R.string.panel_devices_scene_night, Color(0xFF9184D9))
            ).forEach { (id, label, color) ->
                val active = scene == id
                Surface(
                    onClick = {
                        state.devices = applyScene(state.devices, id, KITCHEN)
                        state.scene = id
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) color else TileOff,
                    contentColor = if (active) DarkInk else Color.White,
                    border = if (active) null else BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(label), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

}

/** Detalle del dispositivo abierto, encima de todo el panel; tocar fuera o «atrás» lo cierra. */
@Composable
fun PanelDeviceDetailLayer(state: PanelDevicesState) {
    val d = state.devices.firstOrNull { it.id == state.openId } ?: return
    val close = { state.openId = null }
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xA8080914))
                .clickable(interactionSource = null, indication = null, onClick = close)
        )
        DeviceDetail(
            d,
            state.accent(d),
            onDismiss = close,
            onChange = { transform -> state.change(d.id, transform) },
            onStop = { state.devices = state.devices.update(d.id) { it.copy(target = null) } }
        )
    }
}

@Composable
private fun DeviceTile(d: PanelDevice, accent: Color, modifier: Modifier, onQuick: () -> Unit, onOpen: () -> Unit) {
    val active = d.isActive()
    val background by animateColorAsState(if (active) accent else TileOff, label = "mosaico")
    val content = if (active) DarkInk else Color.White
    val quickLabel = stringResource(
        when {
            d.kind == DeviceKind.COVER && d.position > 0 -> R.string.panel_devices_quick_down
            d.kind == DeviceKind.COVER -> R.string.panel_devices_quick_up
            d.on -> R.string.panel_devices_quick_off
            else -> R.string.panel_devices_quick_on
        },
        d.name
    )
    Column(
        modifier
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (active) DarkInk.copy(alpha = 0.16f) else IconOff)
                .clickable(onClick = onQuick)
                .semantics {
                    contentDescription = quickLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(kindIcon(d.kind), contentDescription = null, tint = if (active) DarkInk else accent, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(d.name, color = content, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Text(statusText(d), color = content.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DeviceDetail(
    d: PanelDevice,
    accent: Color,
    onDismiss: () -> Unit,
    onChange: ((PanelDevice) -> PanelDevice) -> Unit,
    onStop: () -> Unit
) {
    run {
        Surface(
            Modifier.width(520.dp),
            shape = RoundedCornerShape(28.dp),
            color = NocturneSurface,
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(Modifier.padding(start = 28.dp, end = 20.dp, top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (d.isActive()) accent else Color(0xFF4A4D63)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            stringResource(areaLabel(d.areaId)) + " · " + statusText(d),
                            color = Color(0xFFB9B7C9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.panel_devices_close))
                    }
                }
                Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (d.kind) {
                        DeviceKind.LIGHT -> LightDetail(d, accent, onChange)
                        DeviceKind.COVER -> CoverDetail(d, onChange, onStop)
                        DeviceKind.SWITCH -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun LightDetail(d: PanelDevice, accent: Color, onChange: ((PanelDevice) -> PanelDevice) -> Unit) {
    Surface(
        onClick = { onChange { it.copy(on = !it.on) } },
        shape = RoundedCornerShape(18.dp),
        color = if (d.on) IconOff else accent,
        contentColor = if (d.on) Color.White else DarkInk,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(if (d.on) R.string.panel_devices_turn_off else R.string.panel_devices_turn_on), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    Column {
        Row {
            Text(stringResource(R.string.panel_devices_brightness), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.panel_devices_percent, d.brightness), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = d.brightness.toFloat(),
            onValueChange = { v -> onChange { it.copy(brightness = v.toInt(), on = true) } },
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }
    if (d.supportsColor) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.panel_devices_color), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightColor.entries.forEach { c ->
                    val selected = d.color == c
                    val label = stringResource(c.label())
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable { onChange { it.copy(color = c, on = true) } }
                            .semantics {
                                contentDescription = label
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 40.dp else 34.dp)
                                .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .padding(if (selected) 5.dp else 0.dp)
                                .clip(CircleShape)
                                .background(c.value())
                        )
                    }
                }
            }
        }
    } else {
        Text(stringResource(R.string.panel_devices_no_color), color = Color(0xFFB9B7C9), fontSize = 13.sp)
    }
}

@Composable
private fun CoverDetail(d: PanelDevice, onChange: ((PanelDevice) -> PanelDevice) -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Ventana con la persiana: la parte de lamas baja según lo cerrada que esté.
        Box(
            Modifier
                .size(width = 170.dp, height = 210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Sky)
                .border(6.dp, Color(0xFF3A3D52), RoundedCornerShape(14.dp))
        ) {
            if (d.position < 100) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((100 - d.position) / 100f)
                        .background(Color(0xFFD9D3C4))
                        .drawBehind {
                            val gap = 14.dp.toPx()
                            var y = gap
                            while (y < size.height) {
                                drawLine(Color(0xFFB9B2A1), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                                y += gap
                            }
                            drawLine(Color(0xFF8E8778), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 8.dp.toPx())
                        }
                )
            }
        }
        Column(Modifier.weight(1f).height(210.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(statusText(d), fontSize = 26.sp, fontWeight = FontWeight.Light)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverButton(Icons.Filled.KeyboardArrowUp, R.string.panel_devices_up, Modifier.weight(1f)) { onChange { it.copy(target = 100) } }
                CoverButton(Icons.Filled.Stop, R.string.panel_devices_stop, Modifier.weight(1f), onStop)
                CoverButton(Icons.Filled.KeyboardArrowDown, R.string.panel_devices_down, Modifier.weight(1f)) { onChange { it.copy(target = 0) } }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.panel_devices_set_position), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = (d.target ?: d.position).toFloat(),
                onValueChange = { v -> onChange { it.copy(target = v.toInt()) } },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Sky, activeTrackColor = Sky)
            )
        }
    }
}

@Composable
private fun CoverButton(icon: ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = IconOff, contentColor = Color.White, modifier = modifier.height(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(28.dp))
        }
    }
}

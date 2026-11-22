package com.homepantry.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.homepantry.app.R
import com.homepantry.app.data.Greeting
import com.homepantry.app.data.Item
import com.homepantry.app.data.MenuSlotSection
import com.homepantry.app.data.WeatherKind
import com.homepantry.app.data.WeatherSnapshot
import com.homepantry.app.data.greeting
import com.homepantry.app.data.isMorning
import com.homepantry.app.data.skyPhase
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.LocalPanelDimmed
import com.homepantry.app.ui.PanelViewModel
import com.homepantry.app.ui.WeatherState
import com.homepantry.app.ui.flattenAndSort
import com.homepantry.app.ui.theme.NocturneBackground
import com.homepantry.app.ui.theme.NocturneOnPrimary
import com.homepantry.app.ui.theme.NocturnePrimary
import com.homepantry.app.ui.theme.NocturneSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Margen para deshacer un toque sin querer antes de que el producto pase a «en casa». */
private const val STRIKE_DELAY_MILLIS = 3_000L

private val spanish = Locale.forLanguageTag("es-ES")
private val headerDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)
private val clock = DateTimeFormatter.ofPattern("HH:mm", spanish)

/** Sombra suave del texto blanco: se lee sobre cualquier cielo, también el claro de la nieve. */
private val OnSky = TextStyle(color = Color.White, shadow = Shadow(Color(0x59000000), blurRadius = 12f))
private val OnSkySoft = Color.White.copy(alpha = 0.85f)

/** Lila más claro que el primario: sobre la tarjeta translúcida el primario se queda corto de contraste. */
private val SectionLabel = Color(0xFFB3A9F0)
private val CardFill = NocturneSurface.copy(alpha = 0.55f)
private val CardBorder = Color.White.copy(alpha = 0.12f)

/**
 * Panel de la tablet de la cocina (specs 2026-11-21-panel-tablet-cocina y
 * 2026-11-22-panel-cielo-animado): un cielo animado según el tiempo de fondo, y encima la
 * cabecera con el tiempo, el menú de hoy y la lista de la compra. Va sin barra inferior; se sale
 * manteniendo pulsada la fecha.
 */
@Composable
fun PanelScreen(
    appViewModel: AppViewModel,
    panelViewModel: PanelViewModel,
    onOpenRestock: () -> Unit,
    onExitPanel: () -> Unit,
    onConfigureWeather: () -> Unit
) {
    val state by appViewModel.state.collectAsState()
    val now by panelViewModel.now.collectAsState()
    val menu by panelViewModel.menu.collectAsState()
    val weather by panelViewModel.weather.collectAsState()
    val pending = remember(state.items, state.zones) {
        flattenAndSort(state.items.filter { !it.done }, state.zones).map { it.item }
    }
    // Pasada la medianoche, la previsión de ayer no se enseña hasta que llegue la de hoy.
    val snapshot = weather.snapshot?.takeIf { it.isFor(now.toLocalDate()) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = !LocalPanelDimmed.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    Box(Modifier.fillMaxSize()) {
        // Sin pueblo o sin datos, cielo nublado: el panel nunca se queda sin fondo.
        PanelSky(
            kind = snapshot?.kind ?: WeatherKind.CLOUDY,
            phase = skyPhase(now.toLocalTime(), snapshot?.sunriseMinute, snapshot?.sunsetMinute),
            animate = animate
        )
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp, vertical = 24.dp)) {
                PanelHeader(now, weather, snapshot, animate, onExitPanel, onConfigureWeather, panelViewModel::refreshWeather)
                Spacer(Modifier.height(24.dp))
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= maxHeight) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            PanelCard(Modifier.weight(1f).fillMaxHeight(), delayMillis = 0) { MenuColumn(menu) }
                            PanelCard(Modifier.weight(1f).fillMaxHeight(), delayMillis = 120) {
                                ShoppingColumn(appViewModel, pending, onOpenRestock)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            PanelCard(Modifier.fillMaxWidth(), delayMillis = 0) { MenuColumn(menu) }
                            PanelCard(Modifier.fillMaxWidth().weight(1f), delayMillis = 120) {
                                ShoppingColumn(appViewModel, pending, onOpenRestock)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta translúcida sobre el cielo, que entra con un fundido al abrir el panel. */
@Composable
private fun PanelCard(modifier: Modifier, delayMillis: Long, content: @Composable ColumnScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Surface(
        modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * 16.dp.toPx()
        },
        shape = RoundedCornerShape(24.dp),
        color = CardFill,
        contentColor = Color.White,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelHeader(
    now: LocalDateTime,
    weather: WeatherState,
    snapshot: WeatherSnapshot?,
    animate: Boolean,
    onExitPanel: () -> Unit,
    onConfigureWeather: () -> Unit,
    onRetryWeather: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            // Pulsación larga para salir: un toque sin querer no debe sacar a nadie del panel.
            Column(
                Modifier
                    .weight(1f)
                    .combinedClickable(onClick = {}, onLongClick = onExitPanel)
            ) {
                Text(stringResource(greetingText(greeting(now.toLocalTime()))), style = OnSky.copy(color = OnSkySoft), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    now.format(headerDate).replaceFirstChar { it.titlecase(spanish) },
                    style = OnSky,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Clock(now, animate)
            }
            WeatherSummary(weather, snapshot, onConfigureWeather, onRetryWeather)
        }
        if (isMorning(now.toLocalTime()) && snapshot != null && snapshot.hours.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HourlyStrip(snapshot)
        }
    }
}

/** Reloj grande con los dos puntos parpadeando cada segundo (quietos con el panel atenuado). */
@Composable
private fun Clock(now: LocalDateTime, animate: Boolean) {
    val colonAlpha = if (animate) {
        val transition = rememberInfiniteTransition(label = "reloj")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 2_000
                    1f at 0
                    1f at 999
                    0.25f at 1_000
                    0.25f at 1_999
                },
                RepeatMode.Restart
            ),
            label = "dos puntos"
        )
        alpha
    } else {
        1f
    }
    val text = now.format(clock)
    Text(
        buildAnnotatedString {
            append(text.substringBefore(':'))
            withStyle(SpanStyle(color = Color.White.copy(alpha = colonAlpha))) { append(":") }
            append(text.substringAfter(':'))
        },
        style = OnSky,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light
    )
}

@Composable
private fun WeatherSummary(weather: WeatherState, snapshot: WeatherSnapshot?, onConfigureWeather: () -> Unit, onRetryWeather: () -> Unit) {
    when {
        weather.place == null -> Text(
            stringResource(R.string.panel_weather_pick_place),
            style = OnSky,
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onConfigureWeather).padding(8.dp)
        )
        snapshot == null -> Text(
            stringResource(if (weather.failed) R.string.panel_weather_unavailable else R.string.panel_weather_loading),
            style = OnSky.copy(color = OnSkySoft),
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onRetryWeather).padding(8.dp)
        )
        else -> Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(weatherKindText(snapshot.kind)), style = OnSky, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.panel_weather_temp, snapshot.currentTemp), style = OnSky, fontSize = 76.sp, fontWeight = FontWeight.Light)
            }
            Text(
                stringResource(R.string.panel_weather_range, snapshot.maxTemp, snapshot.minTemp, snapshot.rainProbability),
                style = OnSky,
                fontSize = 18.sp
            )
            Text(
                listOf(weather.place.name, weather.place.admin1).filter { it.isNotBlank() }.joinToString(", ") +
                    if (weather.failed) " · " + stringResource(R.string.panel_weather_updated_at, fetchedTime(snapshot)) else "",
                style = OnSky.copy(color = OnSkySoft),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun HourlyStrip(snapshot: WeatherSnapshot) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(snapshot.hours) { hour ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.panel_weather_hour, hour.hour), color = OnSkySoft, fontSize = 14.sp)
                Icon(weatherIcon(hour.kind), contentDescription = null, tint = iconTint(hour.kind), modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.panel_weather_temp, hour.temp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun fetchedTime(snapshot: WeatherSnapshot): String =
    Instant.ofEpochMilli(snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()).format(clock)

private fun greetingText(greeting: Greeting): Int = when (greeting) {
    Greeting.MORNING -> R.string.panel_greeting_morning
    Greeting.AFTERNOON -> R.string.panel_greeting_afternoon
    Greeting.NIGHT -> R.string.panel_greeting_night
}

private fun weatherKindText(kind: WeatherKind): Int = when (kind) {
    WeatherKind.CLEAR -> R.string.panel_weather_kind_clear
    WeatherKind.PARTLY_CLOUDY -> R.string.panel_weather_kind_partly_cloudy
    WeatherKind.CLOUDY -> R.string.panel_weather_kind_cloudy
    WeatherKind.FOG -> R.string.panel_weather_kind_fog
    WeatherKind.DRIZZLE -> R.string.panel_weather_kind_drizzle
    WeatherKind.RAIN -> R.string.panel_weather_kind_rain
    WeatherKind.SNOW -> R.string.panel_weather_kind_snow
    WeatherKind.STORM -> R.string.panel_weather_kind_storm
}

private fun weatherIcon(kind: WeatherKind): ImageVector = when (kind) {
    WeatherKind.CLEAR -> Icons.Filled.WbSunny
    WeatherKind.PARTLY_CLOUDY -> Icons.Filled.FilterDrama
    WeatherKind.CLOUDY -> Icons.Filled.Cloud
    WeatherKind.FOG -> Icons.Filled.Dehaze
    WeatherKind.DRIZZLE -> Icons.Filled.Grain
    WeatherKind.RAIN -> Icons.Filled.Umbrella
    WeatherKind.SNOW -> Icons.Filled.AcUnit
    WeatherKind.STORM -> Icons.Filled.Thunderstorm
}

private fun iconTint(kind: WeatherKind): Color = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY -> Color(0xFFFFD66B)
    WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.STORM -> Color(0xFF9CCBFF)
    else -> Color.White
}

@Composable
private fun ColumnScope.MenuColumn(menu: List<MenuSlotSection>) {
    Text(stringResource(R.string.panel_menu_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    if (menu.isEmpty()) {
        Text(stringResource(R.string.panel_menu_empty), style = MaterialTheme.typography.titleMedium, color = OnSkySoft)
        return
    }
    menu.forEach { section ->
        Text(
            section.slot.label.uppercase(spanish),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = SectionLabel
        )
        section.lines.forEach { line ->
            Text(
                if (line.person == null) line.name else stringResource(R.string.panel_menu_person_line, line.person, line.name),
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ColumnScope.ShoppingColumn(appViewModel: AppViewModel, pending: List<Item>, onOpenRestock: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Tachados a la espera de pasar a «en casa», por id: si otro miembro cambia el producto
    // entretanto, al cumplirse el plazo solo se escribe done = true y no se pisa nada más.
    val striking = remember { mutableStateMapOf<String, Job>() }
    Text(
        pluralStringResource(R.plurals.panel_list_title, pending.size, pending.size),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (pending.isEmpty()) {
            item {
                Text(stringResource(R.string.panel_list_empty), style = MaterialTheme.typography.titleMedium, color = OnSkySoft)
            }
        }
        items(pending, key = { it.id }) { item ->
            val struck = item.id in striking
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        val job = striking.remove(item.id)
                        if (job != null) {
                            job.cancel()
                        } else {
                            striking[item.id] = scope.launch {
                                delay(STRIKE_DELAY_MILLIS)
                                appViewModel.setDone(item, true)
                                striking.remove(item.id)
                            }
                        }
                    }
                    .graphicsLayer { alpha = if (struck) 0.6f else 1f }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckMark(struck)
                Spacer(Modifier.width(16.dp))
                Text(
                    item.name,
                    fontSize = 24.sp,
                    textDecoration = if (struck) TextDecoration.LineThrough else null
                )
            }
        }
    }
    Button(
        onClick = onOpenRestock,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NocturnePrimary, contentColor = NocturneOnPrimary)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.panel_restock_button), fontSize = 20.sp)
    }
}

/** Casilla que se rellena y crece un poco al tachar: se nota el toque desde lejos. */
@Composable
private fun CheckMark(checked: Boolean) {
    val fill by animateColorAsState(if (checked) NocturnePrimary else Color.Transparent, label = "casilla")
    val edge by animateColorAsState(if (checked) NocturnePrimary else Color.White.copy(alpha = 0.6f), label = "borde")
    val scale by animateFloatAsState(if (checked) 1.08f else 1f, label = "escala")
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .border(2.dp, edge, shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = NocturneBackground, modifier = Modifier.size(20.dp))
    }
}

/** Tarjeta de Ajustes del modo panel: interruptor y pueblo del tiempo. */
@Composable
fun PanelSettingsCard(
    panelMode: Boolean,
    placeName: String?,
    onPanelModeChange: (Boolean) -> Unit,
    onPickPlace: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.panel_settings_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.panel_settings_mode), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.panel_settings_mode_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(checked = panelMode, onCheckedChange = onPanelModeChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.panel_settings_place, placeName ?: stringResource(R.string.panel_settings_place_none)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onPickPlace).padding(vertical = 8.dp)
            )
        }
    }
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.MenuSlotSection
import com.homepantry.app.data.WeatherKind
import com.homepantry.app.data.WeatherSnapshot
import com.homepantry.app.data.isMorning
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.PanelViewModel
import com.homepantry.app.ui.WeatherState
import com.homepantry.app.ui.flattenAndSort
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

/**
 * Panel de la tablet de la cocina (spec 2026-11-21-panel-tablet-cocina): el tiempo arriba, el
 * menú de hoy y la lista de la compra. Va sin barra inferior; se sale manteniendo pulsada la fecha.
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

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            PanelHeader(now, weather, onExitPanel, onConfigureWeather, panelViewModel::refreshWeather)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f)) { MenuColumn(menu) }
                        Column(Modifier.weight(1f)) { ShoppingColumn(appViewModel, pending, onOpenRestock) }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column { MenuColumn(menu) }
                        Column(Modifier.weight(1f)) { ShoppingColumn(appViewModel, pending, onOpenRestock) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelHeader(
    now: LocalDateTime,
    weather: WeatherState,
    onExitPanel: () -> Unit,
    onConfigureWeather: () -> Unit,
    onRetryWeather: () -> Unit
) {
    val morning = isMorning(now.toLocalTime())
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsación larga para salir: un toque sin querer no debe sacar a nadie del panel.
            Column(
                Modifier
                    .weight(1f)
                    .combinedClickable(onClick = {}, onLongClick = onExitPanel)
            ) {
                Text(
                    now.format(headerDate).replaceFirstChar { it.titlecase(spanish) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(now.format(clock), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WeatherSummary(weather, now.toLocalDate(), onConfigureWeather, onRetryWeather)
        }
        // Pasada la medianoche, la previsión de ayer no se enseña hasta que llegue la de hoy.
        val snapshot = weather.snapshot?.takeIf { it.isFor(now.toLocalDate()) }
        if (morning && snapshot != null && snapshot.hours.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HourlyStrip(snapshot)
        }
    }
}

@Composable
private fun WeatherSummary(weather: WeatherState, today: LocalDate, onConfigureWeather: () -> Unit, onRetryWeather: () -> Unit) {
    val snapshot = weather.snapshot?.takeIf { it.isFor(today) }
    when {
        weather.place == null -> Text(
            stringResource(R.string.panel_weather_pick_place),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onConfigureWeather).padding(8.dp)
        )
        snapshot == null -> Text(
            stringResource(if (weather.failed) R.string.panel_weather_unavailable else R.string.panel_weather_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onRetryWeather).padding(8.dp)
        )
        else -> Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(snapshot.kind), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.panel_weather_temp, snapshot.currentTemp), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.panel_weather_range, snapshot.maxTemp, snapshot.minTemp, snapshot.rainProbability),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                listOf(weather.place.name, weather.place.admin1).filter { it.isNotBlank() }.joinToString(", ") +
                    if (weather.failed) " · " + stringResource(R.string.panel_weather_updated_at, fetchedTime(snapshot)) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HourlyStrip(snapshot: WeatherSnapshot) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items(snapshot.hours) { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.panel_weather_hour, hour.hour), style = MaterialTheme.typography.bodyMedium)
                Icon(weatherIcon(hour.kind), contentDescription = null, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.panel_weather_temp, hour.temp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun fetchedTime(snapshot: WeatherSnapshot): String =
    Instant.ofEpochMilli(snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()).format(clock)

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

@Composable
private fun ColumnScope.MenuColumn(menu: List<MenuSlotSection>) {
    Text(stringResource(R.string.panel_menu_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    if (menu.isEmpty()) {
        Text(stringResource(R.string.panel_menu_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    menu.forEach { section ->
        Text(section.slot.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        section.lines.forEach { line ->
            Text(
                if (line.person == null) line.name else stringResource(R.string.panel_menu_person_line, line.person, line.name),
                fontSize = 22.sp,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
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
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (pending.isEmpty()) {
            item {
                Text(stringResource(R.string.panel_list_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(pending, key = { it.id }) { item ->
            val struck = item.id in striking
            Row(
                Modifier
                    .fillMaxWidth()
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
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (struck) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (struck) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    item.name,
                    fontSize = 24.sp,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    Button(onClick = onOpenRestock, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.panel_restock_button), fontSize = 20.sp)
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

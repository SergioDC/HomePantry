package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.CalendarMode
import com.homepantry.app.data.DAY_NAMES
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.PAGER_ANCHOR_PAGE
import com.homepantry.app.data.PAGER_PAGE_COUNT
import com.homepantry.app.data.entriesFor
import com.homepantry.app.data.monthGrid
import com.homepantry.app.data.monthLabel
import com.homepantry.app.data.pageForMonth
import com.homepantry.app.data.pageForWeek
import com.homepantry.app.data.pageMonth
import com.homepantry.app.data.pageWeekStart
import com.homepantry.app.data.weekDays
import com.homepantry.app.data.weekRangeLabel
import com.homepantry.app.data.weekStart
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.SegmentedToggle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Calendario del menú: un `HorizontalPager` con una página por semana (o por mes). Deslizar en
 * horizontal cambia de semana o mes; dentro de cada semana se hace scroll vertical.
 * Las flechas y «Hoy» hacen lo mismo con animación.
 */
@Composable
fun MenuCalendarTab(
    state: MenuUiState,
    viewModel: MenuViewModel,
    onOpenDay: (LocalDate) -> Unit,
    onShare: (LocalDate) -> Unit,
    onDuplicate: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val mode = state.position.mode
    val scope = rememberCoroutineScope()

    // El pager se recrea al cambiar de modo para colocarse en la fecha de referencia.
    key(mode) {
        val initialPage = remember {
            when (mode) {
                CalendarMode.WEEK -> pageForWeek(today, state.position.date, PAGER_ANCHOR_PAGE)
                CalendarMode.MONTH ->
                    pageForMonth(YearMonth.from(today), YearMonth.from(state.position.date), PAGER_ANCHOR_PAGE)
            }
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGER_PAGE_COUNT })

        // La fecha de referencia sigue a la página asentada (no a la intermedia de un deslizamiento).
        LaunchedEffect(pagerState, mode) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                when (mode) {
                    CalendarMode.WEEK -> viewModel.setVisibleDate(pageWeekStart(today, page, PAGER_ANCHOR_PAGE))
                    CalendarMode.MONTH ->
                        viewModel.setVisibleMonth(pageMonth(YearMonth.from(today), page, PAGER_ANCHOR_PAGE))
                }
            }
        }

        suspend fun goTo(page: Int) {
            // Saltos largos (p.ej. «Hoy» desde muy lejos) sin animar para no recorrer páginas vacías.
            if (abs(page - pagerState.currentPage) > 1) pagerState.scrollToPage(page)
            else pagerState.animateScrollToPage(page)
        }

        val visibleWeekStart = weekStart(state.position.date)
        val title = when (mode) {
            CalendarMode.WEEK -> weekRangeLabel(visibleWeekStart)
            CalendarMode.MONTH -> monthLabel(YearMonth.from(state.position.date))
        }
        var menuOpen by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { goTo(pagerState.currentPage - 1) } }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { scope.launch { goTo(pagerState.currentPage + 1) } }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                }
                if (mode == CalendarMode.WEEK) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more_cd))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_week)) },
                                onClick = {
                                    menuOpen = false
                                    onShare(visibleWeekStart)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_duplicate_week)) },
                                onClick = {
                                    menuOpen = false
                                    onDuplicate(visibleWeekStart)
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedToggle(
                    options = listOf(
                        CalendarMode.WEEK to stringResource(R.string.menu_mode_week),
                        CalendarMode.MONTH to stringResource(R.string.menu_mode_month)
                    ),
                    selected = mode,
                    onSelect = { viewModel.setMode(it) }
                )
                TextButton(onClick = { scope.launch { goTo(PAGER_ANCHOR_PAGE) } }) {
                    Text(stringResource(R.string.menu_today))
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (mode) {
                    CalendarMode.WEEK -> WeekPage(
                        weekStart = pageWeekStart(today, page, PAGER_ANCHOR_PAGE),
                        entries = state.entries,
                        today = today,
                        onOpenDay = onOpenDay
                    )
                    CalendarMode.MONTH -> MonthPage(
                        month = pageMonth(YearMonth.from(today), page, PAGER_ANCHOR_PAGE),
                        entries = state.entries,
                        today = today,
                        onPickDay = { viewModel.openWeekOf(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekPage(
    weekStart: LocalDate,
    entries: List<MealEntry>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val days = remember(weekStart) { weekDays(weekStart) }
    val weekEntries = remember(entries, weekStart) {
        val keys = days.map { it.toString() }.toSet()
        entries.filter { it.date in keys }
    }
    val byDate = remember(weekEntries) { weekEntries.groupBy { it.date } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (weekEntries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.menu_empty_week),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onOpenDay(if (today in days) today else weekStart) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(stringResource(R.string.menu_empty_week_start)) }
                }
            }
        }
        items(days, key = { it.toString() }) { day ->
            DayRow(
                day = day,
                entries = byDate[day.toString()].orEmpty(),
                isToday = day == today,
                onClick = { onOpenDay(day) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayRow(day: LocalDate, entries: List<MealEntry>, isToday: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DAY_NAMES[day.dayOfWeek.value - 1].take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MealSlot.values().forEach { slot ->
                    val slotEntries = entriesFor(entries, day.toString(), slot)
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp).padding(top = 4.dp)
                        )
                        if (slotEntries.isEmpty()) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                slotEntries.forEach { MealChip(it.name) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealChip(name: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MonthPage(
    month: YearMonth,
    entries: List<MealEntry>,
    today: LocalDate,
    onPickDay: (LocalDate) -> Unit
) {
    val grid = remember(month) { monthGrid(month) }
    val daysWithEntries = remember(entries) { entries.map { it.date }.toSet() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { name ->
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.month == month.month
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (day == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onPickDay(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (day.toString() in daysWithEntries) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

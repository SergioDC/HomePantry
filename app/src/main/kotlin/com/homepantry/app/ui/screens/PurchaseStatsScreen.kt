package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MonthlySpend
import com.homepantry.app.data.TopBy
import com.homepantry.app.data.cheapestStores
import com.homepantry.app.data.lastMonthsSpend
import com.homepantry.app.data.monthSummary
import com.homepantry.app.data.priceRises
import com.homepantry.app.data.spendByStore
import com.homepantry.app.data.topProducts
import com.homepantry.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val esLocale = Locale("es", "ES")

private fun money(value: Double): String = String.format(esLocale, "%,.2f €", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseStatsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val purchases = state.purchases
    val now = remember { Date() }
    var topBy by rememberSaveable { mutableStateOf(TopBy.SPEND) }

    val summary = remember(purchases) { monthSummary(purchases, now) }
    val months = remember(purchases) { lastMonthsSpend(purchases, 6, now) }
    val stores = remember(purchases) { spendByStore(purchases, now) }
    val top = remember(purchases, topBy) { topProducts(purchases, topBy) }
    val rises = remember(purchases) { priceRises(purchases) }
    val cheapest = remember(purchases) { cheapestStores(purchases) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (purchases.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Section(stringResource(R.string.stats_this_month)) {
                    Text(money(summary.total), style = MaterialTheme.typography.headlineMedium)
                    summary.previousTotal?.let { previous ->
                        val diff = summary.total - previous
                        val sign = if (diff >= 0) "+" else "−"
                        val pct = if (previous > 0) " ($sign${(abs(diff) / previous * 100).roundToInt()} %)" else ""
                        Text(
                            stringResource(R.string.stats_vs_previous, "$sign${money(abs(diff))}$pct"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        stringResource(R.string.stats_tickets_avg, summary.ticketCount, money(summary.averagePerTicket)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { Section(stringResource(R.string.stats_last_months)) { MonthBars(months) } }
            item {
                Section(stringResource(R.string.stats_by_store)) {
                    if (stores.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    stores.forEach { store ->
                        LabeledBar(
                            label = store.displayName.ifEmpty { stringResource(R.string.stats_no_store) },
                            value = "${money(store.total)} · ${(store.fraction * 100).roundToInt()} %",
                            fraction = store.fraction.toFloat()
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_top_products)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = topBy == TopBy.SPEND, onClick = { topBy = TopBy.SPEND },
                            label = { Text(stringResource(R.string.stats_by_spend)) }
                        )
                        FilterChip(
                            selected = topBy == TopBy.COUNT, onClick = { topBy = TopBy.COUNT },
                            label = { Text(stringResource(R.string.stats_by_count)) }
                        )
                    }
                    val max = top.maxOfOrNull { if (topBy == TopBy.SPEND) it.total else it.count.toDouble() }
                        ?.takeIf { it > 0 } ?: 1.0
                    top.forEach { product ->
                        val value = if (topBy == TopBy.SPEND) product.total else product.count.toDouble()
                        Box(Modifier.clickable { onOpenProduct(product.normalizedName) }) {
                            LabeledBar(
                                label = product.displayName,
                                value = if (topBy == TopBy.SPEND) money(product.total)
                                else stringResource(R.string.stats_times, product.count),
                                fraction = (value / max).toFloat()
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_price_rises)) {
                    if (rises.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    rises.forEach { rise ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenProduct(rise.normalizedName) },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rise.displayName, Modifier.weight(1f))
                            Text(
                                "${money(rise.firstUnitPrice)} → ${money(rise.latestUnitPrice)} (+${(rise.changeFraction * 100).roundToInt()} %)",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.stats_cheapest)) {
                    if (cheapest.isEmpty()) Text(stringResource(R.string.stats_none_yet))
                    cheapest.forEach { c ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenProduct(c.normalizedName) }) {
                            Text(c.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(
                                    R.string.stats_cheapest_line, c.cheapestStore, (c.savingFraction * 100).roundToInt()
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Bar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun LabeledBar(label: String, value: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        Bar(fraction)
    }
}

@Composable
private fun MonthBars(months: List<MonthlySpend>) {
    val max = months.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    val parse = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val label = remember { SimpleDateFormat("LLL", esLocale) }
    Row(
        Modifier.fillMaxWidth().height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEachIndexed { index, month ->
            val isCurrent = index == months.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (month.total > 0) month.total.roundToInt().toString() else "",
                    style = MaterialTheme.typography.labelSmall, maxLines = 1
                )
                Box(
                    Modifier.width(24.dp)
                        .height((80 * (month.total / max)).toFloat().coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                )
                Text(label.format(parse.parse(month.yearMonth)!!), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

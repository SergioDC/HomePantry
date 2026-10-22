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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.TopBy
import com.homepantry.app.data.cheapestStores
import com.homepantry.app.data.priceRises
import com.homepantry.app.data.purchaseMonths
import com.homepantry.app.data.purchaseYears
import com.homepantry.app.data.purchasesInPeriod
import com.homepantry.app.data.topProducts
import com.homepantry.app.ui.AppViewModel
import java.util.Locale
import kotlin.math.roundToInt

private val esLocale = Locale.forLanguageTag("es-ES")

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
    var topBy by rememberSaveable { mutableStateOf(TopBy.SPEND) }

    val years = remember(purchases) { purchaseYears(purchases) }
    var yearKey by rememberSaveable { mutableStateOf<Int?>(null) }
    var monthKey by rememberSaveable { mutableStateOf<Int?>(null) }
    val year = yearKey?.takeIf { it in years } ?: years.firstOrNull() ?: 0
    val monthsOfYear = remember(purchases, year) { purchaseMonths(purchases, year) }
    val month = monthKey?.takeIf { it in monthsOfYear }
    val top = remember(purchases, topBy, year, month) {
        topProducts(purchasesInPeriod(purchases, year, month), topBy, limit = 10)
    }
    val rises = remember(purchases) { priceRises(purchases) }
    val cheapest = remember(purchases) { cheapestStores(purchases) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
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
                    if (years.size > 1) {
                        ScrollableTabRow(selectedTabIndex = years.indexOf(year).coerceAtLeast(0), edgePadding = 0.dp) {
                            years.forEach { y ->
                                Tab(selected = y == year, onClick = { yearKey = y }, text = { Text(y.toString()) })
                            }
                        }
                    }
                    val monthNames = remember { java.text.DateFormatSymbols(esLocale).months }
                    ScrollableTabRow(selectedTabIndex = monthsOfYear.indexOf(month) + 1, edgePadding = 0.dp) {
                        Tab(
                            selected = month == null, onClick = { monthKey = null },
                            text = { Text(if (years.size > 1) stringResource(R.string.stats_whole_year) else year.toString()) }
                        )
                        monthsOfYear.forEach { m ->
                            Tab(
                                selected = m == month, onClick = { monthKey = m },
                                text = { Text(monthNames[m - 1].replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    val max = top.maxOfOrNull { if (topBy == TopBy.SPEND) it.total else it.count.toDouble() }
                        ?.takeIf { it > 0 } ?: 1.0
                    top.forEach { product ->
                        val value = if (topBy == TopBy.SPEND) product.total else product.count.toDouble()
                        Box(Modifier.clickable { onOpenProduct(product.normalizedName) }) {
                            LabeledBar(
                                label = product.displayName,
                                value = if (topBy == TopBy.SPEND) money(product.total)
                                else pluralStringResource(R.plurals.stats_times, product.count, product.count),
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

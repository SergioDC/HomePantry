package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.lastMonthsSpend
import com.homepantry.app.data.monthStart
import com.homepantry.app.data.storeKey
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.MonthHeader
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SpendMonthBars
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.colorOf
import com.homepantry.app.ui.components.euros
import com.homepantry.app.ui.components.rememberStoreColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detalle de un producto del histórico, con el estilo del panel de gastos: tarjeta con lo
 * gastado en total y su gráfica de 12 meses, y sus compras agrupadas por mes, cada una con la
 * franja de la tienda donde se hizo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    viewModel: AppViewModel,
    normalizedName: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val summary = remember(state.purchaseSummaries, normalizedName) {
        state.purchaseSummaries.firstOrNull { it.normalizedName == normalizedName }
    }
    val storeColors = rememberStoreColors(state.purchaseTickets)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.displayName ?: normalizedName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_detail_not_found))
            }
            return@Scaffold
        }
        val now = remember { Date() }
        val chart = remember(summary, now) { lastMonthsSpend(summary.purchases, 12, now) }
        // Las compras ya vienen de la más reciente a la más antigua, así que los meses también.
        val byMonth = remember(summary) {
            val key = SimpleDateFormat("yyyy-MM", Locale.US)
            summary.purchases.groupBy { key.format(it.date) }
        }
        val esLocale = remember { Locale.forLanguageTag("es-ES") }
        val monthTitle = remember { SimpleDateFormat("LLLL yyyy", esLocale) }
        val dayFormat = remember { SimpleDateFormat("d MMM yyyy", esLocale) }
        val shortDayFormat = remember { SimpleDateFormat("d MMM", esLocale) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "summary") {
                SpendHeaderCard {
                    Text(
                        stringResource(R.string.spend_product_total),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        euros(summary.allTimeTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.purchase_history_last_purchase,
                            summary.purchases.size,
                            shortDayFormat.format(summary.purchases.first().date),
                            summary.purchases.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(
                            R.string.spend_last_unit_price,
                            stringResource(
                                R.string.purchase_history_unit_price,
                                summary.latestUnitPrice,
                                unitSuffix(summary.latestUnit)
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SpendMonthBars(
                        months = chart,
                        selectedYearMonth = null,
                        onSelect = null,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item(key = "purchasesHeader") { SpendSectionTitle(stringResource(R.string.spend_purchases)) }
            byMonth.forEach { (yearMonth, purchases) ->
                item(key = "header/$yearMonth") {
                    MonthHeader(
                        title = monthTitle.format(monthStart(yearMonth)).uppercase(esLocale),
                        detail = pluralStringResource(
                            R.plurals.spend_month_purchases, purchases.size, purchases.size, euros(purchases.sumOf { it.price })
                        )
                    )
                }
                items(purchases, key = { it.id }) { purchase ->
                    StripedCard(color = storeColors.colorOf(storeKey(purchase.store)), onClick = null) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dayFormat.format(purchase.date),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(euros(purchase.price), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.spend_purchase_line,
                                purchase.store?.takeIf { it.isNotBlank() }?.trim()
                                    ?: stringResource(R.string.purchase_history_no_store),
                                formatQuantity(purchase.quantity),
                                unitSuffix(purchase.unit),
                                stringResource(
                                    R.string.purchase_history_unit_price,
                                    purchase.unitPrice(),
                                    unitSuffix(purchase.unit)
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

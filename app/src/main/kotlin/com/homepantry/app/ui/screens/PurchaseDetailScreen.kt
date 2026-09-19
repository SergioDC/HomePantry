package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.monthlySpend
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Detalle de un producto del histórico: gasto por mes + lista de compras. */
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.displayName ?: normalizedName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_detail_not_found))
            }
        } else {
            val monthly = remember(summary) { monthlySpend(summary.purchases) }
            val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        stringResource(R.string.purchase_detail_monthly_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(monthly, key = { it.yearMonth }) { month ->
                    ListItem(
                        headlineContent = { Text(month.yearMonth) },
                        trailingContent = { Text(stringResource(R.string.purchase_detail_amount, month.total)) }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.purchase_detail_history_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(summary.purchases, key = { it.id }) { purchase ->
                    ListItem(
                        headlineContent = { Text(dateFormat.format(purchase.date)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.purchase_detail_line_info,
                                    purchase.store?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.purchase_history_no_store),
                                    formatQuantity(purchase.quantity),
                                    unitSuffix(purchase.unit)
                                )
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(
                                        R.string.purchase_history_unit_price,
                                        purchase.unitPrice(),
                                        unitSuffix(purchase.unit)
                                    )
                                )
                                Text(
                                    stringResource(R.string.purchase_detail_amount, purchase.price),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

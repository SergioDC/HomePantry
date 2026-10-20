package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Todos los productos comprados, agrupados por supermercado, con su último precio unitario
 * (antes era la pestaña «Productos» del historial). Tocar uno abre su detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseProductsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spend_all_products)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (state.purchaseStoreSections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_history_empty))
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.purchaseStoreSections.forEach { section ->
                item(key = "store/${section.storeKey}") {
                    Text(
                        text = section.displayName.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(section.products, key = { "${section.storeKey}/${it.normalizedName}" }) { summary ->
                    ListItem(
                        headlineContent = { Text(summary.displayName) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.purchase_history_last_purchase,
                                    summary.purchases.size,
                                    dateFormat.format(summary.purchases.first().date),
                                    summary.purchases.size
                                )
                            )
                        },
                        trailingContent = {
                            Text(
                                stringResource(
                                    R.string.purchase_history_unit_price,
                                    summary.latestUnitPrice,
                                    unitSuffix(summary.latestUnit)
                                )
                            )
                        },
                        modifier = Modifier.clickable { onOpenProduct(summary.normalizedName) }
                    )
                }
            }
        }
    }
}

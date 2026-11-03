package com.homepantry.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.defaultMergeName
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.colorOf
import com.homepantry.app.ui.components.rememberStoreColors
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Todos los productos comprados, agrupados por supermercado, con su último precio unitario
 * (antes era la pestaña «Productos» del historial). Cada tienda lleva su color, como en el
 * panel de gastos. Tocar un producto abre su detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseProductsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val storeColors = rememberStoreColors(state.purchaseTickets)
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES")) }
    // Modo selección para unir productos: se entra con una pulsación larga. Va por `normalizedName`,
    // así que un producto comprado en dos tiendas queda marcado en las dos.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var mergeName by remember { mutableStateOf<String?>(null) }
    fun toggle(name: String) {
        selected = if (name in selected) selected - name else selected + name
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selected.isEmpty()) {
                        Text(stringResource(R.string.spend_all_products))
                    } else {
                        Text(pluralStringResource(R.plurals.merge_products_selected, selected.size, selected.size))
                    }
                },
                navigationIcon = {
                    if (selected.isEmpty()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                        }
                    } else {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.merge_products_cancel_cd))
                        }
                    }
                },
                actions = {
                    if (selected.size >= 2) {
                        TextButton(onClick = { mergeName = defaultMergeName(state.purchases, selected) }) {
                            Text(stringResource(R.string.merge_products))
                        }
                    }
                }
            )
        }
    ) { padding ->
        mergeName?.let { initial ->
            MergeProductsDialog(
                initialName = initial,
                onConfirm = { name ->
                    viewModel.mergeProducts(selected, name)
                    selected = emptySet()
                    mergeName = null
                },
                onDismiss = { mergeName = null }
            )
        }
        if (state.purchaseStoreSections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_history_empty))
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.purchaseStoreSections.forEach { section ->
                val color = storeColors.colorOf(section.storeKey)
                item(key = "store/${section.storeKey}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(color)
                        Text(
                            text = section.displayName.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        Text(
                            pluralStringResource(R.plurals.spend_products_count, section.products.size, section.products.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(section.products, key = { "${section.storeKey}/${it.normalizedName}" }) { summary ->
                    StripedCard(
                        color = color,
                        onClick = {
                            if (selected.isEmpty()) onOpenProduct(summary.normalizedName) else toggle(summary.normalizedName)
                        },
                        onLongClick = { toggle(summary.normalizedName) },
                        selected = summary.normalizedName in selected
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                summary.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                stringResource(
                                    R.string.purchase_history_unit_price,
                                    summary.latestUnitPrice,
                                    unitSuffix(summary.latestUnit)
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            pluralStringResource(
                                R.plurals.purchase_history_last_purchase,
                                summary.purchases.size,
                                dateFormat.format(summary.purchases.first().date),
                                summary.purchases.size
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

/** Pide el nombre con el que quedan unidos los productos seleccionados. */
@Composable
private fun MergeProductsDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_products_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.merge_products_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.merge_products))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.receipt_review_back)) }
        }
    )
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.DEFAULT_PRODUCTS
import com.homepantry.app.data.DefaultProduct
import com.homepantry.app.ui.AppViewModel

/**
 * Alta rápida de productos habituales agrupados por zona, con checkbox
 * (evita crear cada producto a mano). Los que ya están en la lista actual
 * (mismo nombre, sin distinguir mayúsculas) aparecen deshabilitados para no
 * duplicarlos -- ver AppViewModel.addDefaultItems para el mapeo de zonas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultProductsSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val existingNames = remember(state.items) {
        state.items.map { it.name.trim().lowercase() }.toSet()
    }
    val grouped = remember { DEFAULT_PRODUCTS.groupBy { it.zoneName } }
    var selected by remember {
        mutableStateOf(DEFAULT_PRODUCTS.filterNot { existingNames.contains(it.name.lowercase()) }.toSet())
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            item {
                Text(
                    text = stringResource(R.string.default_products_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.default_products_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            grouped.forEach { (zoneName, products) ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(zoneName, style = MaterialTheme.typography.titleMedium)
                        val selectableInZone = products.filterNot { existingNames.contains(it.name.lowercase()) }
                        val allSelected = selectableInZone.isNotEmpty() && selectableInZone.all { it in selected }
                        TextButton(onClick = {
                            selected = if (allSelected) {
                                selected - selectableInZone.toSet()
                            } else {
                                selected + selectableInZone.toSet()
                            }
                        }) {
                            Text(
                                if (allSelected) stringResource(R.string.default_products_deselect_all)
                                else stringResource(R.string.default_products_select_all)
                            )
                        }
                    }
                }
                items(products, key = { "${it.zoneName}/${it.name}" }) { product ->
                    val alreadyAdded = existingNames.contains(product.name.lowercase())
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = alreadyAdded || product in selected,
                            enabled = !alreadyAdded,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + product else selected - product
                            }
                        )
                        Text(
                            text = if (alreadyAdded) {
                                stringResource(R.string.default_products_already_added, product.name)
                            } else {
                                product.name
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.addDefaultItems(selected.toList())
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) {
                    Text(stringResource(R.string.default_products_add_selected, selected.size))
                }
            }
        }
    }
}

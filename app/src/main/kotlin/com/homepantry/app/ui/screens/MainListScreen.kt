package com.homepantry.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.resolvedZoneColor
import com.homepantry.app.data.zoneDisplayLabel
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.ListViewMode
import com.homepantry.app.ui.SortMode
import com.homepantry.app.ui.components.ItemPillRow
import com.homepantry.app.ui.components.NocturneFab
import com.homepantry.app.ui.components.ProgressBar
import com.homepantry.app.ui.components.SegmentedToggle
import com.homepantry.app.ui.components.ZoneChip

/** Pestaña "Lista": la lista de la compra (SPEC.md sec 1.4, restyle Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: AppViewModel,
    isOnline: Boolean = true,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit = {},
    onQuickAddDefaults: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.main_overflow_cd))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_cd)) },
                            onClick = {
                                menuExpanded = false
                                sortMenuExpanded = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_quick_add_defaults)) },
                            onClick = {
                                menuExpanded = false
                                onQuickAddDefaults()
                            }
                        )
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_newest)) },
                            onClick = { viewModel.setSortMode(SortMode.NEWEST_FIRST); sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_oldest)) },
                            onClick = { viewModel.setSortMode(SortMode.OLDEST_FIRST); sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_sort_alpha)) },
                            onClick = { viewModel.setSortMode(SortMode.ALPHABETICAL); sortMenuExpanded = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = onAddItem, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isOnline) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.main_offline_indicator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.main_error_generic),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            ProgressBar(items = state.items, modifier = Modifier.padding(16.dp))

            SegmentedToggle(
                options = listOf(
                    ListViewMode.GROUPED to stringResource(R.string.main_view_grouped),
                    ListViewMode.FLAT to stringResource(R.string.main_view_flat)
                ),
                selected = state.listViewMode,
                onSelect = { viewModel.setListViewMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoneChip(
                    label = stringResource(R.string.main_all_zones_tab),
                    colorHex = null,
                    selected = state.selectedZoneId == "ALL",
                    onClick = { viewModel.selectZone("ALL") }
                )
                state.zones.sortedBy { it.order }.forEachIndexed { index, zone ->
                    ZoneChip(
                        label = zoneDisplayLabel(zone, state.zones),
                        colorHex = resolvedZoneColor(zone, index),
                        selected = state.selectedZoneId == zone.id,
                        onClick = { viewModel.selectZone(zone.id) }
                    )
                }
            }

            // Filtro por tienda (versión ligera): solo aparece si algún producto pendiente
            // tiene tienda asignada, para no meter una fila vacía cuando nadie la usa.
            if (state.pendingStores.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoneChip(
                        label = stringResource(R.string.main_all_zones_tab),
                        colorHex = null,
                        selected = state.selectedStore == "ALL",
                        onClick = { viewModel.selectStore("ALL") }
                    )
                    state.pendingStores.forEach { store ->
                        ZoneChip(
                            label = store,
                            colorHex = null,
                            selected = state.selectedStore == store,
                            onClick = { viewModel.selectStore(store) }
                        )
                    }
                }
            }

            when {
                // `weight(1f)` en vez de solo `fillMaxSize()`: dentro de un Column,
                // un hijo sin peso se mide contra la altura completa del Column (no
                // la que queda libre tras la barra de progreso/toggle/chips de
                // arriba), así que el contenido centrado acababa desplazado hacia
                // abajo en vez de centrado en el espacio realmente visible.
                state.loading -> Box(
                    Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                // state.flatRows (no state.items): tras filtrar a solo pendientes, puede
                // haber productos en el hogar (inventario en zonas) sin que quede nada por
                // comprar -- en ese caso también hay que mostrar el mensaje de vacío, no una
                // lista en blanco.
                state.flatRows.isEmpty() -> Box(
                    Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.main_empty_list),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                // bottom = 96.dp (no solo 16.dp): deja hueco para que el último producto no
                // quede tapado detrás del FAB flotante de "añadir".
                state.listViewMode == ListViewMode.FLAT -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
                ) {
                    items(state.flatRows, key = { it.item.id }) { row ->
                        ItemPillRow(
                            item = row.item,
                            zoneName = row.zoneName,
                            zoneColor = row.zoneColor,
                            onToggleDone = { viewModel.toggleDone(row.item) },
                            onDelete = { viewModel.deleteItem(row.item.id) },
                            onEdit = { onEditItem(row.item) }
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
                ) {
                    state.sections.forEach { section ->
                        item {
                            Text(
                                text = section.zone.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(section.items, key = { it.id }) { product ->
                            ItemPillRow(
                                item = product,
                                onToggleDone = { viewModel.toggleDone(product) },
                                onDelete = { viewModel.deleteItem(product.id) },
                                onEdit = { onEditItem(product) }
                            )
                        }
                    }
                }
            }
        }
    }
}

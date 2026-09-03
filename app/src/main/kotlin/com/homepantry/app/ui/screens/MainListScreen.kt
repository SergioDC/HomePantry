package com.listacasa.app.ui.screens

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.zoneColorFor
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.ListViewMode
import com.listacasa.app.ui.SortMode
import com.listacasa.app.ui.components.ItemPillRow
import com.listacasa.app.ui.components.NocturneFab
import com.listacasa.app.ui.components.ProgressBar
import com.listacasa.app.ui.components.SegmentedToggle
import com.listacasa.app.ui.components.ZoneChip

/** Pestaña "Lista": la lista de la compra (SPEC.md sec 1.4, restyle Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: AppViewModel,
    isOnline: Boolean = true,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit = {},
    onEditName: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
                            text = { Text(stringResource(R.string.main_clear_done)) },
                            onClick = {
                                menuExpanded = false
                                showClearConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_edit_name)) },
                            onClick = {
                                menuExpanded = false
                                onEditName()
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
                        zone = zone,
                        colorHex = zoneColorFor(index),
                        selected = state.selectedZoneId == zone.id,
                        onClick = { viewModel.selectZone(zone.id) }
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.main_error_generic))
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.main_empty_list))
                }
                state.listViewMode == ListViewMode.FLAT -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.flatRows, key = { it.item.id }) { row ->
                        ItemPillRow(
                            item = row.item,
                            zoneName = row.zoneName,
                            updatedInZoneLabel = if (row.item.done) {
                                stringResource(R.string.main_updated_in_zone, row.zoneName)
                            } else null,
                            onToggleDone = { viewModel.toggleDone(row.item) },
                            onDelete = { viewModel.deleteItem(row.item.id) },
                            onEdit = { onEditItem(row.item) }
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
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

    if (showClearConfirm) {
        val doneCount = state.items.count { it.done }
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.main_clear_done_confirm_title)) },
            text = { Text(stringResource(R.string.main_clear_done_confirm_message, doneCount)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDone()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.main_clear_done_confirm_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

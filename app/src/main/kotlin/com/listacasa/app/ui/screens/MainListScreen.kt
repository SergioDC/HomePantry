package com.listacasa.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.SortMode
import com.listacasa.app.ui.components.ProductCard
import com.listacasa.app.ui.components.ProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SPEC.md sec 1.4: pantalla principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: AppViewModel,
    isOnline: Boolean = true,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit = {},
    onManageZones: () -> Unit,
    onEditName: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale("es", "ES")).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(today.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.main_search_cd))
                    }
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.main_sort_cd))
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
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.main_add_item_cd))
            }
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

            if (showSearchBar) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.main_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            ProgressBar(items = state.items, modifier = Modifier.padding(16.dp))

            val allTabs = listOf("ALL" to stringResource(R.string.main_all_zones_tab)) +
                state.zones.sortedBy { it.order }.map { it.id to it.name }
            val selectedIndex = allTabs.indexOfFirst { it.first == state.selectedZoneId }.coerceAtLeast(0)

            ScrollableTabRow(selectedTabIndex = selectedIndex) {
                allTabs.forEachIndexed { index, (id, label) ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectZone(id) },
                        text = { Text(label) }
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
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                ProductCard(
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

package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ItemPillRow
import com.homepantry.app.ui.filterItemsByQuery

/** Pestaña "Buscar": búsqueda siempre visible, sin toggle (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: AppViewModel, onEditItem: (Item) -> Unit) {
    val state by viewModel.state.collectAsState()
    val zoneNameById = state.zones.associate { it.id to it.name }
    val results = filterItemsByQuery(state.items, state.searchQuery)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_buscar)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            Text(
                text = stringResource(R.string.search_results_count, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(results, key = { it.id }) { product ->
                        ItemPillRow(
                            item = product,
                            zoneName = zoneNameById[product.zone],
                            showDeleteAction = false,
                            onToggleDone = { viewModel.toggleDone(product) },
                            onEdit = { onEditItem(product) }
                        )
                    }
                }
            }
        }
    }
}

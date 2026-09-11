package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.Unit as ItemUnit
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.filterItemsByQuery

/**
 * Pestaña "Buscar": consulta de solo lectura -- dice dónde está un producto (zona) y si
 * está pendiente o ya lo tienes. Añadir a la Lista o a una Zona es cosa de esas pantallas,
 * no de aquí, así que no hay checkbox, ni edición, ni alta rápida de sugerencias.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val zoneNameById = state.zones.associate { it.id to it.name }
    val query = state.searchQuery.trim()
    // Con query vacía no se muestra nada (ni el inventario entero): filterItemsByQuery
    // por defecto devuelve todos los productos si no hay texto, que es justo lo que no
    // queremos aquí -- forzar a escribir algo antes de listar resultados.
    val results = if (query.isEmpty()) emptyList() else filterItemsByQuery(state.items, state.searchQuery)

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
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isEmpty()) stringResource(R.string.search_empty)
                        else stringResource(R.string.search_no_results),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(results, key = { it.id }) { product ->
                        SearchResultRow(item = product, zoneName = zoneNameById[product.zone] ?: "")
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: Item, zoneName: String) {
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${item.qty} $unitLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (item.done) stringResource(R.string.search_status_owned) else stringResource(R.string.search_status_pending),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
    }
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.RestockSection
import com.homepantry.app.data.restockSections
import com.homepantry.app.ui.AppViewModel

/**
 * «Se ha acabado…» del panel de cocina: lo que hay en casa en botones grandes, por zonas. Un toque
 * lo pasa a la lista sin teclado; «Nuevo» abre la hoja de añadir de siempre (con el micrófono del
 * teclado para dictar).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PanelRestockScreen(
    appViewModel: AppViewModel,
    /** Abre la hoja de añadir global de la app: así cuenta como hoja abierta para el panel. */
    onAddNew: () -> Unit,
    onBack: () -> Unit
) {
    val state by appViewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // Lo tocado deja de estar en casa y saldría de la cuadrícula: se guarda aquí para seguir
    // viéndolo marcado «en la lista» mientras no se salga de la pantalla.
    val added = remember { mutableStateListOf<Item>() }
    val noZone = stringResource(R.string.panel_restock_no_zone)
    val sections = remember(state.items, state.zones, query, added.size) {
        val inHouseIds = state.items.filter { it.done }.map { it.id }.toSet()
        val shown = state.items + added.filter { it.id !in inHouseIds }.map { it.copy(done = true) }
        restockSections(shown.distinctBy { it.id }, state.zones, query, noZone)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.panel_restock_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    FilledTonalButton(onClick = onAddNew, modifier = Modifier.padding(end = 12.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Text(stringResource(R.string.panel_restock_new), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.panel_restock_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (sections.isEmpty()) {
                item {
                    Text(stringResource(R.string.panel_restock_empty), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(sections, key = { it.zoneId ?: "" }) { section ->
                RestockZone(section, isAdded = { item -> added.any { it.id == item.id } }) { item ->
                    if (added.none { it.id == item.id }) {
                        added.add(item)
                        appViewModel.setDone(item, false)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestockZone(section: RestockSection, isAdded: (Item) -> Boolean, onTap: (Item) -> Unit) = Column {
    Text(section.zoneName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        section.items.forEach { item ->
            val inList = isAdded(item)
            FilterChip(
                selected = inList,
                onClick = { onTap(item) },
                label = { Text(item.name, fontSize = 20.sp, modifier = Modifier.padding(vertical = 12.dp)) },
                leadingIcon = if (inList) {
                    { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.panel_restock_in_list_cd)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.heightIn(min = 56.dp)
            )
        }
    }
}

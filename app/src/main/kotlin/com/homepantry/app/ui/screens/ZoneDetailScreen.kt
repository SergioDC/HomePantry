package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.homepantry.app.ui.components.NocturneFab
import com.homepantry.app.ui.groupAndSort

/** Pantalla de detalle de una zona, abierta desde el dashboard "Almacén" (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    viewModel: AppViewModel,
    zoneId: String,
    onBack: () -> Unit,
    onManageZones: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val zone = state.zones.firstOrNull { it.id == zoneId }
    val zoneItems = groupAndSort(state.items, state.zones, filterZoneId = zoneId).firstOrNull()?.items ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zone?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zone_detail_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.zone_detail_edit_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = onAddItem, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        if (zoneItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.main_empty_list))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(zoneItems, key = { it.id }) { product ->
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

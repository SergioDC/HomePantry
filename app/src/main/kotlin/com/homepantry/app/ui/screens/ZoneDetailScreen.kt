package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.ZONE_COLORS
import com.homepantry.app.data.isProtectedZone
import com.homepantry.app.data.resolvedZoneColor
import com.homepantry.app.data.subzonesOf
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ItemPillRow
import com.homepantry.app.ui.components.NocturneFab
import com.homepantry.app.ui.components.ZoneChip
import com.homepantry.app.ui.groupAndSort

/**
 * Pantalla de detalle de una zona, abierta desde el dashboard "Almacén" (Nocturne).
 * Renombrar y eliminar la zona viven aquí (no en Ajustes) -- Ajustes es solo
 * ajustes/datos generales; crear zonas nuevas vive en el dashboard. Las subzonas
 * (un solo nivel) se crean y listan aquí también, solo para zonas raíz -- una
 * subzona no puede tener sus propias subzonas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    viewModel: AppViewModel,
    zoneId: String,
    onBack: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Item) -> Unit,
    onOpenZone: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val zone = state.zones.firstOrNull { it.id == zoneId }
    // Solo lo que tienes (done=true): lo pendiente de esta zona vive en Lista de la
    // compra, no aquí -- si no, marcar "agotado" o "añadir a la lista" no lo quitaba
    // realmente de la vista de la zona (seguía apareciendo, solo sin tachar).
    val zoneItems = (groupAndSort(state.items, state.zones, filterZoneId = zoneId).firstOrNull()?.items ?: emptyList())
        .filter { it.done }
    val subzones = subzonesOf(zoneId, state.zones)
    var itemPendingDelete by remember { mutableStateOf<Item?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteZoneConfirm by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAddSubzoneDialog by remember { mutableStateOf(false) }
    var newSubzoneName by remember { mutableStateOf("") }

    val protected = zone != null && isProtectedZone(zone)
    val canDeleteZone = zone != null && !protected && state.zones.size > 1 && subzones.isEmpty()
    val zoneIndex = state.zones.sortedBy { it.order }.indexOfFirst { it.id == zoneId }
    val currentColor = zone?.let { resolvedZoneColor(it, zoneIndex.coerceAtLeast(0)) }

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
                    if (zone != null) {
                        IconButton(onClick = { showColorDialog = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.zone_detail_color_cd))
                        }
                    }
                    if (!protected) {
                        IconButton(onClick = {
                            renameText = zone?.name ?: ""
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.zone_detail_edit_cd))
                        }
                    }
                    if (canDeleteZone) {
                        IconButton(onClick = { showDeleteZoneConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.zones_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = onAddItem, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (zone != null && zone.parentZoneId == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subzones.forEachIndexed { index, subzone ->
                        ZoneChip(
                            label = subzone.name,
                            colorHex = resolvedZoneColor(subzone, index),
                            selected = false,
                            onClick = { onOpenZone(subzone.id) }
                        )
                    }
                    AssistChip(
                        onClick = { showAddSubzoneDialog = true },
                        label = { Text(stringResource(R.string.zone_detail_new_subzone_chip)) }
                    )
                }
            }
            if (zoneItems.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.main_empty_list),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    // bottom = 96.dp: deja hueco para que el último producto no quede tapado
                    // detrás del FAB flotante de "añadir".
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
                ) {
                    items(zoneItems, key = { it.id }) { product ->
                        ItemPillRow(
                            item = product,
                            dimWhenDone = false,
                            onToggleDone = { viewModel.toggleDone(product) },
                            onDelete = { itemPendingDelete = product },
                            onEdit = { onEditItem(product) }
                        )
                    }
                }
            }
        }
    }

    val pendingItem = itemPendingDelete
    if (pendingItem != null) {
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            title = { Text(stringResource(R.string.zone_detail_remove_title, pendingItem.name)) },
            text = { Text(stringResource(R.string.zone_detail_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    // Forzar a pendiente (no toggleDone): si ya estaba pendiente, no queremos
                    // que "añadir a la lista" lo marque como tenido por error.
                    viewModel.editItem(pendingItem.copy(done = false))
                    itemPendingDelete = null
                }) { Text(stringResource(R.string.zone_detail_remove_to_list)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(pendingItem.id)
                    itemPendingDelete = null
                }) { Text(stringResource(R.string.zone_detail_remove_delete), color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (showRenameDialog && zone != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.zone_detail_edit_cd)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameZone(zone.id, renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank()
                ) { Text(stringResource(R.string.add_item_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddSubzoneDialog && zone != null) {
        AlertDialog(
            onDismissRequest = { showAddSubzoneDialog = false; newSubzoneName = "" },
            title = { Text(stringResource(R.string.zone_detail_new_subzone_title)) },
            text = {
                OutlinedTextField(
                    value = newSubzoneName,
                    onValueChange = { newSubzoneName = it },
                    label = { Text(stringResource(R.string.zones_new_zone_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSubzoneName.isNotBlank()) {
                            viewModel.createZone(newSubzoneName.trim(), zone.id)
                        }
                        newSubzoneName = ""
                        showAddSubzoneDialog = false
                    },
                    enabled = newSubzoneName.isNotBlank()
                ) { Text(stringResource(R.string.add_item_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubzoneDialog = false; newSubzoneName = "" }) { Text("Cancelar") }
            }
        )
    }

    if (showColorDialog && zone != null) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text(stringResource(R.string.zone_detail_color_cd)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    ZONE_COLORS.forEach { hex ->
                        val swatchColor = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = hex.equals(currentColor, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(swatchColor, CircleShape)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.updateZoneColor(zone.id, hex)
                                    showColorDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text(stringResource(R.string.zone_detail_color_close)) }
            }
        )
    }

    if (showDeleteZoneConfirm && zone != null) {
        val otros = state.zones.firstOrNull { isProtectedZone(it) && it.id != zone.id }
            ?: state.zones.firstOrNull { it.id != zone.id }
        val affectedCount = state.items.count { it.zone == zone.id }
        AlertDialog(
            onDismissRequest = { showDeleteZoneConfirm = false },
            title = { Text(stringResource(R.string.zones_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zones_delete_confirm_message, affectedCount)) },
            confirmButton = {
                TextButton(onClick = {
                    if (otros != null) {
                        viewModel.deleteZone(zone.id, otros.id)
                    }
                    showDeleteZoneConfirm = false
                    onBack()
                }) { Text(stringResource(R.string.zones_delete_confirm_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteZoneConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

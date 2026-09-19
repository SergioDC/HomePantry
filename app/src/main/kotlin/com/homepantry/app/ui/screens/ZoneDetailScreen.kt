package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.homepantry.app.ui.zoneDetailSections

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
    onAddItem: (String) -> Unit,
    onEditItem: (Item) -> Unit,
    onOpenZone: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val zone = state.zones.firstOrNull { it.id == zoneId }
    // Solo lo que tienes (done=true): lo pendiente de esta zona vive en Lista de la
    // compra, no aquí -- si no, marcar "agotado" o "añadir a la lista" no lo quitaba
    // realmente de la vista de la zona (seguía apareciendo, solo sin tachar).
    // Una zona raíz muestra además sus subzonas, cada una como sección propia.
    val sections = zoneDetailSections(zoneId, state.items, state.zones)
    val subzones = subzonesOf(zoneId, state.zones)
    var itemPendingDelete by remember { mutableStateOf<Item?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteZoneConfirm by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAddSubzoneDialog by remember { mutableStateOf(false) }
    var newSubzoneName by remember { mutableStateOf("") }
    // Ids de las subzonas colapsadas; por defecto todas expandidas.
    var collapsedIds by rememberSaveable { mutableStateOf(setOf<String>()) }

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
            NocturneFab(onClick = { onAddItem(zoneId) }, contentDescription = stringResource(R.string.main_add_item_cd))
        }
    ) { padding ->
        // Con subzonas hay un encabezado por sección; sin ellas la lista es la de siempre.
        val showHeaders = sections.size > 1
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // bottom = 96.dp: deja hueco para que el último producto no quede tapado
            // detrás del FAB flotante de "añadir".
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp)
        ) {
            sections.forEach { section ->
                val collapsed = section.isSubzone && section.zone.id in collapsedIds
                if (showHeaders) {
                    item(key = "header/${section.zone.id}") {
                        ZoneSectionHeader(
                            title = section.zone.name,
                            count = section.items.size,
                            colorHex = if (section.isSubzone) {
                                resolvedZoneColor(section.zone, subzones.indexOfFirst { it.id == section.zone.id }.coerceAtLeast(0))
                            } else {
                                currentColor
                            },
                            collapsible = section.isSubzone,
                            collapsed = collapsed,
                            onToggleCollapse = {
                                val id = section.zone.id
                                collapsedIds = if (id in collapsedIds) collapsedIds - id else collapsedIds + id
                            },
                            onOpen = if (section.isSubzone) ({ onOpenZone(section.zone.id) }) else null,
                            onAdd = { onAddItem(section.zone.id) }
                        )
                    }
                }
                if (collapsed) return@forEach
                if (section.items.isEmpty()) {
                    item(key = "empty/${section.zone.id}") {
                        Text(
                            text = stringResource(
                                if (showHeaders) R.string.zone_detail_section_empty else R.string.main_empty_list
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
                items(section.items, key = { it.id }) { product ->
                    ItemPillRow(
                        item = product,
                        dimWhenDone = false,
                        onToggleDone = { viewModel.toggleDone(product) },
                        onDelete = { itemPendingDelete = product },
                        onEdit = { onEditItem(product) }
                    )
                }
            }
            if (zone != null && zone.parentZoneId == null) {
                item(key = "new-subzone") {
                    OutlinedButton(
                        onClick = { showAddSubzoneDialog = true },
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text(stringResource(R.string.zone_detail_new_subzone_chip)) }
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

/**
 * Encabezado de una sección (zona o subzona) con su color, contador y un "+"
 * que añade un producto directamente a ella. Si `collapsible`, tocar el encabezado
 * colapsa o expande la sección; si `onOpen` no es null, un botón abre la pantalla
 * propia de esa subzona.
 */
@Composable
private fun ZoneSectionHeader(
    title: String,
    count: Int,
    colorHex: String?,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onOpen: (() -> Unit)?,
    onAdd: () -> Unit
) {
    val dotColor = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(if (collapsible) Modifier.clickable(onClick = onToggleCollapse) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collapsible) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = stringResource(
                    if (collapsed) R.string.zone_detail_expand_cd else R.string.zone_detail_collapse_cd,
                    title
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if (dotColor != null) {
            Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = if (dotColor != null) 8.dp else 0.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onOpen != null) {
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.zone_detail_open_subzone_cd, title))
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zone_detail_add_to_section_cd, title))
        }
    }
}

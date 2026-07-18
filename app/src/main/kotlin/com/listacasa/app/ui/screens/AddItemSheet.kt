package com.listacasa.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.OpenFoodFactsClient
import com.listacasa.app.data.parseQtyOrDefault
import com.listacasa.app.data.zoneColorFor
import com.listacasa.app.data.Unit as ItemUnit
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.BarcodeScannerView
import com.listacasa.app.ui.components.ZoneChip
import com.listacasa.app.ui.findPendingDuplicateByBarcode
import kotlinx.coroutines.launch

/**
 * SPEC.md sec 1.5: añadir producto, con foto y escaneo de código de barras.
 * Si `itemToEdit` no es null, el formulario se precarga y "Guardar" actualiza
 * ese producto en vez de crear uno nuevo (cierre de huecos §1: edición completa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    viewModel: AppViewModel,
    initialZoneId: String?,
    itemToEdit: Item? = null,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val zones = state.zones.sortedBy { it.order }
    val isEditing = itemToEdit != null

    var name by remember(itemToEdit) { mutableStateOf(itemToEdit?.name ?: "") }
    var qtyText by remember(itemToEdit) { mutableStateOf(itemToEdit?.qty?.toString() ?: "1") }
    var unit by remember(itemToEdit) {
        mutableStateOf(
            itemToEdit?.let { runCatching { ItemUnit.valueOf(it.unit) }.getOrDefault(ItemUnit.UD) } ?: ItemUnit.UD
        )
    }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var note by remember(itemToEdit) { mutableStateOf(itemToEdit?.note ?: "") }
    var selectedZoneId by remember(zones, itemToEdit) {
        mutableStateOf(itemToEdit?.zone ?: initialZoneId?.takeIf { it != "ALL" } ?: zones.firstOrNull()?.id ?: "")
    }
    var newZoneName by remember { mutableStateOf("") }
    var photoUri by remember(itemToEdit) { mutableStateOf<Uri?>(null) }
    var barcode by remember(itemToEdit) { mutableStateOf(itemToEdit?.barcode) }
    var barcodeNotFound by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var duplicateItem by remember { mutableStateOf<Item?>(null) }

    val scope = rememberCoroutineScope()
    val qty = parseQtyOrDefault(qtyText)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = if (isEditing) stringResource(R.string.add_item_edit_title) else stringResource(R.string.add_item_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_item_name)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            item {
                if (barcodeNotFound) {
                    Text(
                        stringResource(R.string.add_item_barcode_not_found),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (barcode != null) {
                    Text(
                        "Código: $barcode",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text(stringResource(R.string.add_item_qty)) },
                        isError = qty == null,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        OutlinedTextField(
                            value = unit.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.add_item_unit)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { unitMenuExpanded = true }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            ItemUnit.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.label) },
                                    onClick = {
                                        unit = candidate
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.zones_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    zones.forEachIndexed { index, zone ->
                        ZoneChip(
                            zone = zone,
                            colorHex = zoneColorFor(index),
                            selected = zone.id == selectedZoneId,
                            onClick = { selectedZoneId = zone.id }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text(stringResource(R.string.add_item_new_zone)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (newZoneName.isNotBlank()) {
                                viewModel.createZone(newZoneName.trim())
                                newZoneName = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("+") }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.add_item_note)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            item {
                if (isEditing && photoUri == null && !itemToEdit?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = itemToEdit?.photoUrl,
                        contentDescription = stringResource(R.string.add_item_existing_photo_cd),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.add_item_photo)) }
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) { Text(stringResource(R.string.add_item_scan)) }
                }
            }
            item {
                Button(
                    onClick = {
                        val validQty = qty ?: return@Button
                        val zoneToUse = selectedZoneId
                        val localPhotoUri = photoUri
                        val existing = itemToEdit
                        if (existing != null) {
                            val updated = existing.copy(
                                name = name.trim(),
                                qty = validQty,
                                unit = unit.name,
                                note = note.trim().ifBlank { null },
                                zone = zoneToUse,
                                barcode = barcode
                            )
                            scope.launch {
                                viewModel.editItem(updated)
                                if (localPhotoUri != null) {
                                    runCatching { viewModel.attachPhoto(updated.id, localPhotoUri) }
                                }
                            }
                        } else {
                            val newItem = Item(
                                name = name.trim(),
                                qty = validQty,
                                unit = unit.name,
                                note = note.trim().ifBlank { null },
                                zone = zoneToUse,
                                barcode = barcode
                            )
                            scope.launch {
                                val newItemId = viewModel.addItem(newItem)
                                if (localPhotoUri != null) {
                                    runCatching { viewModel.attachPhoto(newItemId, localPhotoUri) }
                                }
                            }
                        }
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && selectedZoneId.isNotBlank() && qty != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) {
                    Text(if (isEditing) stringResource(R.string.add_item_save_edit) else stringResource(R.string.add_item_save))
                }
            }
        }
    }

    if (showScanner) {
        Dialog(onDismissRequest = { showScanner = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerView(
                    onBarcodeDetected = { detected ->
                        showScanner = false
                        barcode = detected
                        scope.launch {
                            runCatching { OpenFoodFactsClient.api().getProduct(detected) }
                                .onSuccess { response ->
                                    val productName = response.product?.productName
                                    if (response.status == 1 && !productName.isNullOrBlank()) {
                                        name = productName
                                        barcodeNotFound = false
                                    } else {
                                        barcodeNotFound = true
                                    }
                                }
                                .onFailure { barcodeNotFound = true }
                        }
                        if (!isEditing) {
                            duplicateItem = findPendingDuplicateByBarcode(state.items, detected)
                        }
                    }
                )
            }
        }
    }

    val pendingDuplicate = duplicateItem
    if (pendingDuplicate != null) {
        AlertDialog(
            onDismissRequest = { duplicateItem = null },
            title = { Text(stringResource(R.string.add_item_duplicate_title)) },
            text = { Text(stringResource(R.string.add_item_duplicate_message, pendingDuplicate.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.incrementQty(pendingDuplicate, qty ?: 1.0)
                    duplicateItem = null
                    onDismiss()
                }) { Text(stringResource(R.string.add_item_duplicate_sum)) }
            },
            dismissButton = {
                TextButton(onClick = { duplicateItem = null }) { Text(stringResource(R.string.add_item_duplicate_new)) }
            }
        )
    }
}

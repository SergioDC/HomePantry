package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.homepantry.app.R
import com.homepantry.app.data.DEFAULT_PRODUCTS
import com.homepantry.app.data.Item
import com.homepantry.app.data.OpenFoodFactsClient
import com.homepantry.app.data.parseQtyOrDefault
import com.homepantry.app.data.resolvedZoneColor
import com.homepantry.app.data.subzonesOf
import com.homepantry.app.data.Unit as ItemUnit
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.BarcodeScannerView
import com.homepantry.app.ui.components.ZoneChip
import com.homepantry.app.ui.findPendingDuplicateByBarcode
import kotlinx.coroutines.launch

private data class NameSuggestion(val name: String, val unit: ItemUnit?)

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
    isFromZone: Boolean = false,
    itemToEdit: Item? = null,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val zones = state.zones.sortedBy { it.order }
    val rootZones = zones.filter { it.parentZoneId == null }
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
    var store by remember(itemToEdit) { mutableStateOf(itemToEdit?.store ?: "") }
    var selectedZoneId by remember(itemToEdit) {
        mutableStateOf(itemToEdit?.zone ?: initialZoneId?.takeIf { it != "ALL" } ?: "")
    }
    // El destino es siempre un único id de zona; si es una subzona, la fila de raíces
    // marca su padre y la fila "Subzona" la marca a ella.
    val selectedZone = zones.firstOrNull { it.id == selectedZoneId }
    val selectedRootId = selectedZone?.parentZoneId ?: selectedZone?.id
    val subzoneOptions = selectedRootId?.let { subzonesOf(it, state.zones) } ?: emptyList()
    var newZoneName by remember { mutableStateOf("") }
    var photoUri by remember(itemToEdit) { mutableStateOf<Uri?>(null) }
    var barcode by remember(itemToEdit) { mutableStateOf(itemToEdit?.barcode) }
    var barcodeNotFound by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var duplicateItem by remember { mutableStateOf<Item?>(null) }

    val scope = rememberCoroutineScope()
    val qty = parseQtyOrDefault(qtyText)

    // Autocompletar mientras se escribe: primero productos que ya existen en el hogar (para no
    // crear duplicados con variaciones de nombre), y de no haber suficientes, del catálogo de
    // productos habituales (DEFAULT_PRODUCTS). Al elegir una sugerencia también se rellena la
    // unidad conocida de ese producto. Lista inline (no popup) para no depender del anclaje de
    // un DropdownMenu dentro del LazyColumn del sheet.
    val existingSuggestions = remember(state.items) {
        state.items.distinctBy { it.name.lowercase() }
            .map { NameSuggestion(it.name, runCatching { ItemUnit.valueOf(it.unit) }.getOrNull()) }
    }
    val defaultSuggestions = remember {
        DEFAULT_PRODUCTS.map { NameSuggestion(it.name, runCatching { ItemUnit.valueOf(it.unit) }.getOrNull()) }
    }
    val nameSuggestions = remember(name, existingSuggestions, isEditing) {
        if (isEditing || name.isBlank()) {
            emptyList()
        } else {
            fun matches(suggestion: NameSuggestion) =
                suggestion.name.contains(name, ignoreCase = true) && !suggestion.name.equals(name, ignoreCase = true)

            val fromExisting = existingSuggestions.filter(::matches)
            val existingNamesLower = existingSuggestions.map { it.name.lowercase() }.toSet()
            val fromDefaults = defaultSuggestions.filter { matches(it) && it.name.lowercase() !in existingNamesLower }
            (fromExisting + fromDefaults).take(6)
        }
    }

    // Autocompletar la tienda contra las que ya has usado antes (versión ligera: texto
    // libre, sin pantalla de gestión de tiendas propia).
    val existingStores = remember(state.items) {
        state.items.mapNotNull { it.store }.distinct()
    }
    val storeSuggestions = remember(store, existingStores) {
        if (store.isBlank()) {
            emptyList()
        } else {
            existingStores.filter { it.contains(store, ignoreCase = true) && !it.equals(store, ignoreCase = true) }
                .take(5)
        }
    }

    // Rellena la zona por defecto en cuanto llegan las zonas (pueden no estar cargadas
    // todavía al abrir la modal) sin pisar una selección manual del usuario -- p.ej. al
    // crear una zona nueva desde el "+" de abajo, la lista de zonas cambia pero la
    // selección actual debe conservarse.
    LaunchedEffect(zones) {
        if (selectedZoneId.isBlank() || zones.none { it.id == selectedZoneId }) {
            rootZones.firstOrNull()?.let { selectedZoneId = it.id }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoUri = uri
    }

    // El formulario es largo (nombre, cantidad, zonas, nota, foto...) -- si el
    // sheet abre solo "parcialmente expandido" (comportamiento por defecto de
    // Material3) se ve a medias y hay que arrastrarlo; lo abrimos siempre a
    // pantalla completa.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // El manejo de "atrás" propio del sheet cierra la hoja entera incluso
        // con el teclado abierto (p.ej. al pulsar el botón de cerrar teclado,
        // que también manda un evento atrás) -- lo desactivamos y llevamos el
        // control nosotros abajo: si hay teclado, solo se oculta; si no, se
        // cierra la hoja.
        properties = ModalBottomSheetDefaults.properties(shouldDismissOnBackPress = false)
    ) {
        // El WindowInsets.ime "reactivo" leído en composición no reflejaba bien
        // el estado real del teclado dentro de la ventana propia del sheet (por
        // eso atrás no hacía nada). Consultamos la vista directamente, en el
        // momento del propio evento, para tener el estado real del teclado.
        val keyboardController = LocalSoftwareKeyboardController.current
        val view = LocalView.current
        BackHandler {
            val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeVisible) keyboardController?.hide() else onDismiss()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
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
            items(nameSuggestions, key = { "suggestion/${it.name}" }) { suggestion ->
                Text(
                    text = suggestion.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            name = suggestion.name
                            suggestion.unit?.let { unit = it }
                        }
                        .padding(vertical = 8.dp)
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
                    rootZones.forEach { zone ->
                        ZoneChip(
                            label = zone.name,
                            colorHex = resolvedZoneColor(zone, zones.indexOfFirst { it.id == zone.id }),
                            selected = zone.id == selectedRootId,
                            onClick = { selectedZoneId = zone.id }
                        )
                    }
                }
            }
            if (subzoneOptions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.add_item_subzone),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subzoneOptions.forEach { subzone ->
                            ZoneChip(
                                label = subzone.name,
                                colorHex = resolvedZoneColor(subzone, zones.indexOfFirst { it.id == subzone.id }),
                                selected = subzone.id == selectedZoneId,
                                // Tocar la subzona ya elegida la deselecciona y vuelve a la raíz.
                                onClick = {
                                    selectedZoneId = if (subzone.id == selectedZoneId) selectedRootId.orEmpty() else subzone.id
                                }
                            )
                        }
                    }
                }
            }
            if (isFromZone) {
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
            }
            item {
                val error = state.error
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text(stringResource(R.string.add_item_store)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            items(storeSuggestions, key = { "store-suggestion/$it" }) { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { store = suggestion }
                        .padding(vertical = 8.dp)
                )
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
                // Los botones de foto (`photoPicker`) y de escáner (`showScanner`) se quitaron de la
                // interfaz; su código se conserva a propósito porque la consulta a OpenFoodFacts
                // puede volver a usarse más adelante.
            }
            item {
                Button(
                    onClick = {
                        val validQty = qty ?: return@Button
                        val zoneToUse = selectedZoneId
                        val localPhotoUri = photoUri
                        val existing = itemToEdit
                        // Añadido desde una Zona = ya está en el inventario (no pendiente de compra);
                        // añadido desde la Lista = falta por comprar.
                        if (existing != null) {
                            val updated = existing.copy(
                                name = name.trim(),
                                qty = validQty,
                                unit = unit.name,
                                note = note.trim().ifBlank { null },
                                store = store.trim().ifBlank { null },
                                zone = zoneToUse,
                                barcode = barcode
                            )
                            viewModel.editItem(updated, localPhotoUri)
                        } else {
                            // Si ya existe un producto con este nombre (p.ej. estaba en una Zona
                            // como "tienes" y ahora te has quedado sin él), reutilizarlo en vez de
                            // crear un duplicado: se actualiza con los datos del formulario y pasa
                            // a pendiente/tienes según desde dónde se añade.
                            val duplicate = state.items.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                            if (duplicate != null) {
                                val reactivated = duplicate.copy(
                                    qty = validQty,
                                    unit = unit.name,
                                    note = note.trim().ifBlank { null },
                                    store = store.trim().ifBlank { null },
                                    zone = zoneToUse,
                                    barcode = barcode,
                                    done = isFromZone
                                )
                                viewModel.editItem(reactivated, localPhotoUri)
                            } else {
                                val newItem = Item(
                                    name = name.trim(),
                                    qty = validQty,
                                    unit = unit.name,
                                    note = note.trim().ifBlank { null },
                                    store = store.trim().ifBlank { null },
                                    zone = zoneToUse,
                                    barcode = barcode,
                                    done = isFromZone
                                )
                                viewModel.createItem(newItem, localPhotoUri)
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

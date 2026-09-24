package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.ParsedReceipt
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.data.RECEIPT_UNITS
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.knownStores
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import java.util.Locale

private data class EditableLine(val name: String, val qtyText: String, val unit: String, val priceText: String)

private fun formatPrice(price: Double): String =
    String.format(Locale("es", "ES"), "%.2f", price)

/**
 * Lista editable de las líneas detectadas en el ticket antes de guardarlas,
 * con el supermercado detectado y, por línea, cantidad y unidad.
 * Nada se escribe en Firestore hasta que el usuario pulsa "Guardar
 * compras" (spec "Alcance": revisión obligatoria, sin guardado silencioso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(
    viewModel: AppViewModel,
    initialReceipt: ParsedReceipt,
    ticketPhotoUri: Uri?,
    notice: String? = null,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var store by remember(initialReceipt) { mutableStateOf(initialReceipt.store.orEmpty()) }
    val lines = remember(initialReceipt) {
        mutableStateListOf(
            *initialReceipt.lines.map {
                EditableLine(it.name, formatQuantity(it.quantity), it.unit, formatPrice(it.price))
            }.toTypedArray()
        )
    }
    // Autocompletar el supermercado contra los ya usados en compras anteriores.
    val previousStores = remember(state.purchases) { knownStores(state.purchases) }
    val storeSuggestions = remember(store, previousStores) {
        if (store.isBlank()) {
            emptyList()
        } else {
            previousStores.filter { it.contains(store, ignoreCase = true) && !it.equals(store, ignoreCase = true) }
                .take(5)
        }
    }
    // El resultado de Gemini solo debe descartarse pulsando "Volver" o "Guardar":
    // un swipe, un toque fuera o el botón atrás no deben tirar la interpretación
    // y obligar a reenviar la foto.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            item {
                Text(stringResource(R.string.receipt_review_title), style = MaterialTheme.typography.titleLarge)
            }
            // Un Snackbar del Scaffold queda detrás de esta hoja modal y caduca
            // antes de que el usuario la cierre; el aviso va aquí, fijo.
            if (notice != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text(stringResource(R.string.receipt_review_store)) },
                    singleLine = true,
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
            itemsIndexed(lines) { index, line ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = line.name,
                            onValueChange = { newValue -> lines[index] = line.copy(name = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_name)) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { lines.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.receipt_review_delete_line_cd))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = line.qtyText,
                            onValueChange = { newValue -> lines[index] = line.copy(qtyText = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_qty)) },
                            modifier = Modifier.weight(1f)
                        )
                        UnitField(
                            unit = line.unit,
                            onSelected = { newUnit -> lines[index] = line.copy(unit = newUnit) },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        OutlinedTextField(
                            value = line.priceText,
                            onValueChange = { newValue -> lines[index] = line.copy(priceText = newValue) },
                            label = { Text(stringResource(R.string.receipt_review_price)) },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { lines.add(EditableLine("", "1", RECEIPT_UNITS.first(), "")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.receipt_review_add_line)) }
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.receipt_review_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.receipt_review_back)) }
                    Button(
                        onClick = {
                            val parsed = lines.mapNotNull { line ->
                                val price = line.priceText.replace(",", ".").toDoubleOrNull()
                                if (line.name.isBlank() || price == null) {
                                    null
                                } else {
                                    // Una cantidad ilegible o no positiva no bloquea el guardado: cuenta como 1.
                                    val quantity = line.qtyText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
                                    ParsedReceiptLine(name = line.name.trim(), price = price, quantity = quantity, unit = line.unit)
                                }
                            }
                            viewModel.savePurchaseBatch(
                                ParsedReceipt(store = store.trim().ifBlank { null }, lines = parsed),
                                ticketPhotoUri
                            )
                            onDismiss()
                        },
                        enabled = lines.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.receipt_review_save)) }
                }
            }
        }
    }
}

/** Selector de unidad (ud/kg/l) de una línea; mismo patrón de campo + menú que `AddItemSheet`. */
@Composable
private fun UnitField(unit: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = unitSuffix(unit),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.receipt_review_unit)) },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RECEIPT_UNITS.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(unitSuffix(candidate)) },
                    onClick = {
                        onSelected(candidate)
                        expanded = false
                    }
                )
            }
        }
    }
}

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.homepantry.app.data.parseUserPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.TicketDatePickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EditableLine(val name: String, val qtyText: String, val unit: String, val priceText: String) {
    /** Las líneas sin nombre se descartan al guardar; las que tienen nombre necesitan un precio válido. */
    fun hasInvalidPrice(): Boolean = name.isNotBlank() && parseUserPrice(priceText) == null
}

private fun formatPrice(price: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.2f", price)

/**
 * Lista editable de las líneas detectadas en el ticket antes de guardarlas,
 * con el supermercado y la fecha detectados y, por línea, cantidad y unidad.
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
    // Sin fecha leída del ticket se pide elegirla: guardarlo con la de hoy lo mandaría a otro mes.
    var ticketDate by remember(initialReceipt) { mutableStateOf(initialReceipt.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es-ES")) }
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
    // El resultado de Gemini solo debe descartarse pulsando "Volver" o "Guardar": por eso la hoja
    // no atiende a gestos. Con los gestos activos, al llegar arriba de la lista y seguir
    // arrastrando la hoja empezaba a cerrarse, el veto a cerrarla la dejaba a medio bajar y los
    // botones quedaban fuera de la pantalla sin forma de volver a ellos.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
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
                item {
                    val date = ticketDate
                    // Un campo de solo lectura no recibe el toque: lo recoge la capa transparente de encima.
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = date?.let { dateFormat.format(it).replaceFirstChar { c -> c.uppercase() } }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ticket_date_label)) },
                            placeholder = { Text(stringResource(R.string.ticket_date_choose)) },
                            trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        when {
                                            date == null -> R.string.ticket_date_missing
                                            date == initialReceipt.date -> R.string.ticket_date_read
                                            else -> R.string.ticket_date_manual
                                        }
                                    )
                                )
                            },
                            isError = date == null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(Modifier.matchParentSize().clickable { showDatePicker = true })
                    }
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
                                isError = line.hasInvalidPrice(),
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
                item { Box(Modifier.padding(bottom = 12.dp)) }
            }
            // Fuera de la lista: los botones se ven siempre, por larga que sea o con el teclado abierto.
            HorizontalDivider()
            val total = lines.sumOf { if (it.name.isBlank()) 0.0 else parseUserPrice(it.priceText) ?: 0.0 }
            Text(
                stringResource(R.string.receipt_review_total, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.receipt_review_back)) }
                Button(
                    onClick = {
                        val parsed = lines.mapNotNull { line ->
                            val price = parseUserPrice(line.priceText)
                            if (line.name.isBlank() || price == null) {
                                null
                            } else {
                                // Una cantidad ilegible o no positiva no bloquea el guardado: cuenta como 1.
                                val quantity = line.qtyText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
                                ParsedReceiptLine(name = line.name.trim(), price = price, quantity = quantity, unit = line.unit)
                            }
                        }
                        viewModel.savePurchaseBatch(
                            ParsedReceipt(store = store.trim().ifBlank { null }, lines = parsed, date = ticketDate),
                            ticketPhotoUri
                        )
                        onDismiss()
                    },
                    // Una línea con nombre y precio ilegible no se descarta en silencio: se marca y bloquea el guardado.
                    enabled = lines.isNotEmpty() && lines.none { it.hasInvalidPrice() } && ticketDate != null,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.receipt_review_save)) }
            }
        }
    }

    if (showDatePicker) {
        TicketDatePickerDialog(
            initialDay = ticketDate ?: Date(),
            onConfirm = {
                ticketDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
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

package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
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
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.TicketDatePickerDialog
import com.homepantry.app.ui.components.colorForNewTicket
import com.homepantry.app.ui.components.euros
import com.homepantry.app.ui.components.rememberStoreColors
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
 * Sigue el estilo del detalle de un ticket en Gastos: tarjeta de resumen arriba
 * y cada línea en una tarjeta con la franja del color de la tienda.
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
    // El color que tendrá el ticket en Gastos, para que la revisión ya se vea como quedará.
    val storeColors = rememberStoreColors(state.purchaseTickets)
    val storeColor = storeColors.colorForNewTicket(store)
    val namedLines = lines.count { it.name.isNotBlank() }
    val total = lines.sumOf { if (it.name.isBlank()) 0.0 else parseUserPrice(it.priceText) ?: 0.0 }
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
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "title") {
                    Text(stringResource(R.string.receipt_review_title), style = MaterialTheme.typography.titleLarge)
                }
                // Un Snackbar del Scaffold queda detrás de esta hoja modal y caduca
                // antes de que el usuario la cierre; el aviso va aquí, fijo.
                if (notice != null) {
                    item(key = "notice") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                // La misma tarjeta de resumen que el detalle de un ticket guardado, pero editable.
                item(key = "summary") {
                    SpendHeaderCard {
                        OutlinedTextField(
                            value = store,
                            onValueChange = { store = it },
                            label = { Text(stringResource(R.string.receipt_review_store)) },
                            leadingIcon = { ColorDot(storeColor) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        storeSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { store = suggestion }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorDot(storeColors.colorForNewTicket(suggestion))
                                Text(
                                    suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        Text(
                            euros(total),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            pluralStringResource(R.plurals.spend_products_count, namedLines, namedLines),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TicketDateRow(
                            date = ticketDate,
                            readFromTicket = ticketDate != null && ticketDate == initialReceipt.date,
                            dateFormat = dateFormat,
                            onClick = { showDatePicker = true }
                        )
                    }
                }
                item(key = "productsHeader") {
                    SpendSectionTitle(stringResource(R.string.spend_ticket_products))
                }
                itemsIndexed(lines) { index, line ->
                    StripedCard(color = storeColor, onClick = null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = line.name,
                                onValueChange = { newValue -> lines[index] = line.copy(name = newValue) },
                                label = { Text(stringResource(R.string.receipt_review_name)) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { lines.removeAt(index) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.receipt_review_delete_line_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                if (lines.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.receipt_review_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item(key = "addLine") {
                    OutlinedButton(
                        onClick = { lines.add(EditableLine("", "1", RECEIPT_UNITS.first(), "")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) { Text(stringResource(R.string.receipt_review_add_line)) }
                }
            }
            // Fuera de la lista: los botones se ven siempre, por larga que sea o con el teclado abierto.
            // El total se repite aquí porque la tarjeta de arriba sale de pantalla al bajar por la lista.
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorDot(storeColor)
                Text(
                    stringResource(R.string.receipt_review_total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(euros(total), style = MaterialTheme.typography.titleMedium)
            }
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

/**
 * Fecha del ticket dentro de la tarjeta de resumen, como en el detalle de un ticket guardado;
 * tocarla abre el calendario. Debajo se dice de dónde sale y, si falta, va en rojo porque sin
 * ella no se puede guardar.
 */
@Composable
private fun TicketDateRow(date: Date?, readFromTicket: Boolean, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    val tint = if (date == null) MaterialTheme.colorScheme.error else LocalContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                date?.let { dateFormat.format(it).replaceFirstChar { c -> c.uppercase() } }
                    ?: stringResource(R.string.ticket_date_choose),
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.ticket_date_edit_cd),
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            stringResource(
                when {
                    date == null -> R.string.ticket_date_missing
                    readFromTicket -> R.string.ticket_date_read
                    else -> R.string.ticket_date_manual
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (date == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
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

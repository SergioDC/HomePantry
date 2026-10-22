package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Purchase
import com.homepantry.app.data.RECEIPT_UNITS
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.parseUserPrice
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.DeleteTicketDialog
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.TicketDatePickerDialog
import com.homepantry.app.ui.components.colorOf
import com.homepantry.app.ui.components.euros
import com.homepantry.app.ui.components.rememberStoreColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Detalle de un ticket, con el estilo del panel de gastos: arriba la tarjeta con la tienda (en
 * su color), el total y la fecha (tocarla abre un calendario para corregirla); debajo, cada
 * producto en su tarjeta con la franja de la tienda. Tocar un producto lo edita. La papelera de
 * arriba borra el ticket entero (con confirmación); la de cada línea la borra con «Deshacer».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseTicketDetailScreen(
    viewModel: AppViewModel,
    ticketKey: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val ticket = remember(state.purchases, ticketKey) {
        state.purchaseTickets.firstOrNull { it.key == ticketKey }
    }
    val storeColors = rememberStoreColors(state.purchaseTickets)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val longDateFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es-ES")) }
    var showDeleteTicket by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var purchasePendingEdit by remember { mutableStateOf<Purchase?>(null) }
    val lineDeletedText = stringResource(R.string.purchase_ticket_line_deleted)
    val undoText = stringResource(R.string.purchase_ticket_undo)

    // Un ticket que ya no existe (borrado aquí o desde otro móvil) devuelve a la pantalla anterior.
    LaunchedEffect(ticket == null) {
        if (ticket == null) onBack()
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    fun deleteLine(purchase: Purchase) {
        viewModel.deletePurchase(purchase.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = lineDeletedText,
                actionLabel = undoText,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restorePurchase(purchase)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (ticket != null) {
                        Text(ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    if (ticket != null) {
                        IconButton(onClick = { showDeleteTicket = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.purchase_ticket_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        if (ticket != null) {
            val color = storeColors.colorOf(ticket.storeKey)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "summary") {
                    SpendHeaderCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(color)
                            Text(
                                ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            euros(ticket.total),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            pluralStringResource(R.plurals.spend_products_count, ticket.purchases.size, ticket.purchases.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Los tickets guardados antes de leer la fecha impresa llevan la del escaneo: se corrige aquí.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                longDateFormat.format(ticket.date).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.ticket_date_edit_cd),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item(key = "productsHeader") {
                    SpendSectionTitle(stringResource(R.string.spend_ticket_products))
                }
                items(ticket.purchases, key = { it.id }) { purchase ->
                    StripedCard(color = color, onClick = { purchasePendingEdit = purchase }) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                purchase.rawName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(euros(purchase.price), style = MaterialTheme.typography.titleMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.purchase_ticket_line_info,
                                    formatQuantity(purchase.quantity),
                                    unitSuffix(purchase.unit),
                                    stringResource(
                                        R.string.purchase_history_unit_price,
                                        purchase.unitPrice(),
                                        unitSuffix(purchase.unit)
                                    )
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    // Con una sola línea, borrarla equivale a borrar el ticket: se pide
                                    // confirmación (la pantalla se cerraría y no habría dónde deshacer).
                                    if (ticket.purchases.size == 1) showDeleteTicket = true else deleteLine(purchase)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.purchase_ticket_delete_line_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteTicket && ticket != null) {
        DeleteTicketDialog(
            ticket = ticket,
            onConfirm = {
                viewModel.deleteTicket(ticket.key)
                showDeleteTicket = false
            },
            onDismiss = { showDeleteTicket = false }
        )
    }

    if (showDatePicker && ticket != null) {
        TicketDatePickerDialog(
            initialDay = ticket.date,
            onConfirm = { day ->
                viewModel.updateTicketDate(ticket.key, day)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    val pendingEdit = purchasePendingEdit
    if (pendingEdit != null) {
        EditPurchaseDialog(
            purchase = pendingEdit,
            onSave = { edited ->
                viewModel.updatePurchase(edited)
                purchasePendingEdit = null
            },
            onDismiss = { purchasePendingEdit = null }
        )
    }
}

/** Edita nombre, cantidad, unidad y precio de una línea ya guardada. */
@Composable
private fun EditPurchaseDialog(
    purchase: Purchase,
    onSave: (Purchase) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(purchase) { mutableStateOf(purchase.rawName) }
    var qtyText by remember(purchase) { mutableStateOf(formatQuantity(purchase.quantity)) }
    var unit by remember(purchase) { mutableStateOf(purchase.unit) }
    var priceText by remember(purchase) {
        mutableStateOf(String.format(Locale.forLanguageTag("es-ES"), "%.2f", purchase.price))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_ticket_edit_line_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.receipt_review_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text(stringResource(R.string.receipt_review_qty)) },
                        modifier = Modifier.weight(1f)
                    )
                    EditUnitField(
                        unit = unit,
                        onSelected = { unit = it },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.receipt_review_price)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            val price = parseUserPrice(priceText)
            TextButton(
                onClick = {
                    val quantity = qtyText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: purchase.quantity
                    onSave(
                        purchase.copy(
                            rawName = name.trim(),
                            quantity = quantity,
                            unit = unit,
                            price = price ?: purchase.price
                        )
                    )
                },
                enabled = name.isNotBlank() && price != null
            ) { Text(stringResource(R.string.add_item_save_edit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_ticket_delete_cancel)) }
        }
    )
}

/** Selector de unidad (ud/kg/l), mismo patrón que `ReceiptReviewSheet`. */
@Composable
private fun EditUnitField(unit: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
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

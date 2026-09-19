package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Purchase
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.unitPrice
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.DeleteTicketDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Detalle de un ticket: sus líneas con cantidad, precio unitario y total. La papelera de
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }
    var showDeleteTicket by remember { mutableStateOf(false) }
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
                        Text(
                            stringResource(
                                R.string.purchase_ticket_title,
                                ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                                dateFormat.format(ticket.date)
                            )
                        )
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item(key = "summary") {
                    Text(
                        text = stringResource(R.string.purchase_ticket_summary, ticket.purchases.size, ticket.total),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(ticket.purchases, key = { it.id }) { purchase ->
                    ListItem(
                        headlineContent = { Text(purchase.rawName) },
                        supportingContent = {
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
                                )
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.purchase_detail_amount, purchase.price))
                                IconButton(
                                    onClick = {
                                        // Con una sola línea, borrarla equivale a borrar el ticket: se pide
                                        // confirmación (la pantalla se cerraría y no habría dónde deshacer).
                                        if (ticket.purchases.size == 1) showDeleteTicket = true else deleteLine(purchase)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.purchase_ticket_delete_line_cd)
                                    )
                                }
                            }
                        }
                    )
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
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Ticket
import com.homepantry.app.data.ticketsByMonth
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.DeleteTicketDialog
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pestaña "Por mes" del historial: filtro por supermercado y, por cada mes, su gasto total
 * con los tickets escaneados dentro. Un ticket que parece un duplicado lleva una marca y un
 * botón «Eliminar» (con confirmación).
 */
@Composable
fun PurchaseMonthsTab(
    viewModel: AppViewModel,
    onOpenTicket: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedStoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var ticketPendingDelete by remember { mutableStateOf<Ticket?>(null) }
    val dayFormat = remember { SimpleDateFormat("d MMM", Locale("es", "ES")) }
    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale("es", "ES")) }

    val allTickets = remember(state.purchases) { state.purchaseTickets }
    // Supermercados presentes, del que tiene el ticket más reciente al más antiguo (`storeKey` vacío = sin súper).
    val stores = remember(allTickets) { allTickets.distinctBy { it.storeKey }.map { it.storeKey to it.store } }
    // Si el filtro elegido ya no existe (se borraron todos sus tickets) se vuelve a «Todos».
    val selected = selectedStoreKey?.takeIf { key -> stores.any { it.first == key } }
    val months = remember(allTickets, selected) {
        ticketsByMonth(allTickets.filter { selected == null || it.storeKey == selected })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "filters") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selected == null,
                    onClick = { selectedStoreKey = null },
                    label = { Text(stringResource(R.string.purchase_tickets_all)) }
                )
                stores.forEach { (key, name) ->
                    FilterChip(
                        selected = selected == key,
                        onClick = { selectedStoreKey = key },
                        label = { Text(name.ifEmpty { stringResource(R.string.purchase_history_no_store) }) }
                    )
                }
            }
        }
        months.forEach { month ->
            item(key = "month/${month.yearMonth}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = monthFormat.format(month.tickets.first().date).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.purchase_detail_amount, month.total),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            items(month.tickets, key = { it.key }) { ticket ->
                TicketCard(
                    ticket = ticket,
                    dateText = dayFormat.format(ticket.date),
                    onOpen = { onOpenTicket(ticket.key) },
                    onDelete = { ticketPendingDelete = ticket }
                )
            }
        }
    }

    val pending = ticketPendingDelete
    if (pending != null) {
        DeleteTicketDialog(
            ticket = pending,
            onConfirm = {
                viewModel.deleteTicket(pending.key)
                ticketPendingDelete = null
            },
            onDismiss = { ticketPendingDelete = null }
        )
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    dateText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.purchase_ticket_summary, ticket.purchases.size, ticket.total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (ticket.possibleDuplicate) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.purchase_tickets_duplicate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.purchase_tickets_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

package com.homepantry.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.Ticket
import java.text.SimpleDateFormat
import java.util.Locale

/** Confirmación de borrar un ticket entero (lo usan la lista y el detalle de tickets). */
@Composable
fun DeleteTicketDialog(
    ticket: Ticket,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }
    val store = ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_ticket_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.purchase_ticket_delete_message,
                    store,
                    dateFormat.format(ticket.date),
                    ticket.total,
                    ticket.purchases.size
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.purchase_ticket_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_ticket_delete_cancel)) }
        }
    )
}

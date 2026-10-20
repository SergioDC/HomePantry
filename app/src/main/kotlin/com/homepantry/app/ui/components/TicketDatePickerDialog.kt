package com.homepantry.app.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.localDayToUtcMillis
import com.homepantry.app.data.utcMillisToLocalDay
import java.util.Calendar
import java.util.Date

/**
 * Calendario para elegir la fecha de un ticket (en la revisión y en el detalle). No deja elegir
 * días futuros: un ticket siempre es de hoy o de antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDatePickerDialog(
    initialDay: Date?,
    onConfirm: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val todayMillis = remember { localDayToUtcMillis(Date()) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay?.let { localDayToUtcMillis(it) },
        selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= currentYear
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val selected = state.selectedDateMillis
            TextButton(
                onClick = { if (selected != null) onConfirm(utcMillisToLocalDay(selected)) },
                enabled = selected != null
            ) { Text(stringResource(R.string.ticket_date_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_ticket_delete_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

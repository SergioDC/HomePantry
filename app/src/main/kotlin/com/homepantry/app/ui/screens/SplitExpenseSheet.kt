package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitExpenseError
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.expenseCategoryOf
import com.homepantry.app.data.isDateInMonth
import com.homepantry.app.data.localDayToUtcMillis
import com.homepantry.app.data.parseCents
import com.homepantry.app.data.splitEqually
import com.homepantry.app.data.utcMillisToLocalDay
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.IconTile
import com.homepantry.app.ui.components.MemberAvatar
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.color
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.label
import com.homepantry.app.ui.components.centsText
import com.homepantry.app.ui.components.memberColor
import com.homepantry.app.ui.components.memberColors
import com.homepantry.app.ui.components.splitMemberName
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private val esLocale = Locale.forLanguageTag("es-ES")

/** Primer y último día del mes [monthKey] ("yyyy-MM") como fechas locales. */
private fun monthBounds(monthKey: String): Pair<Date, Date> {
    val month = YearMonth.parse(monthKey)
    val zone = ZoneId.systemDefault()
    return Date.from(month.atDay(1).atStartOfDay(zone).toInstant()) to
        Date.from(month.atEndOfMonth().atStartOfDay(zone).toInstant())
}

/** Fecha por defecto de un gasto nuevo: hoy si cae en el mes (o si es una actividad); si no, el día 1 del mes. */
private fun defaultDate(month: SplitMonth): Date {
    val today = Date()
    return if (month.activity || isDateInMonth(today, month.monthKey)) today else monthBounds(month.monthKey).first
}

/**
 * Alta y edición de un gasto del mes, con estado local como AddItemSheet. [onSave] valida y
 * devuelve los errores; si vienen vacíos, el gasto ya se está guardando y la hoja se cierra.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitExpenseSheet(
    state: SplitUiState,
    month: SplitMonth,
    currentUid: String,
    expense: SplitExpense?,
    onSave: (
        title: String,
        amountText: String,
        payerId: String,
        participants: List<String>,
        date: Date?,
        reimbursement: Boolean
    ) -> Set<SplitExpenseError>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val reimbursement = expense?.reimbursement == true
    var title by remember { mutableStateOf(expense?.title.orEmpty()) }
    var amountText by remember {
        mutableStateOf(expense?.let { String.format(esLocale, "%d,%02d", it.amountCents / 100, it.amountCents % 100) }.orEmpty())
    }
    var payerId by remember {
        mutableStateOf(expense?.payerId ?: currentUid.takeIf { it in month.memberIds }.orEmpty())
    }
    var participants by remember { mutableStateOf(expense?.participants?.toSet() ?: month.memberIds.toSet()) }
    var date by remember { mutableStateOf(expense?.date ?: defaultDate(month)) }
    var errors by remember { mutableStateOf(emptySet<SplitExpenseError>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", esLocale) }

    // Al editar un gasto antiguo puede haber participantes que ya no son del mes: se muestran para
    // que se puedan desmarcar.
    val participantOptions = (month.memberIds + (expense?.participants ?: emptyList())).distinct()
    val colors = remember(month.memberIds) { memberColors(month.memberIds) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // El contenido tiene scroll propio: con los gestos de la hoja activos, el arrastre del
        // scroll se confundía con cerrar la hoja (mismo arreglo que ReceiptReviewSheet).
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabecera con el icono y el color del tipo, que cambian mientras se escribe el concepto.
            val category = expenseCategoryOf(title)
            val tint = if (reimbursement) MaterialTheme.colorScheme.onSurfaceVariant else category.color()
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(if (reimbursement) Icons.Filled.SwapHoriz else category.icon(), tint, size = 48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(if (expense == null) R.string.split_expense_new_title else R.string.split_expense_edit_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (reimbursement) stringResource(R.string.split_reimbursement_title) else category.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = tint
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.split_expense_title)) },
                singleLine = true,
                isError = SplitExpenseError.TITLE in errors,
                supportingText = if (SplitExpenseError.TITLE in errors) {
                    { Text(stringResource(R.string.split_error_title)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.split_expense_amount)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = SplitExpenseError.AMOUNT in errors,
                supportingText = if (SplitExpenseError.AMOUNT in errors) {
                    { Text(stringResource(R.string.split_error_amount)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            SpendSectionTitle(stringResource(R.string.split_expense_payer))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                month.memberIds.forEach { id ->
                    MemberChip(
                        name = splitMemberName(state, id),
                        color = colors.memberColor(id),
                        selected = payerId == id,
                        onClick = { payerId = id }
                    )
                }
            }
            FieldError(SplitExpenseError.PAYER in errors, R.string.split_error_payer)

            SpendSectionTitle(stringResource(R.string.split_expense_participants))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                participantOptions.forEach { id ->
                    val selected = id in participants
                    MemberChip(
                        name = splitMemberName(state, id),
                        color = colors.memberColor(id),
                        selected = selected,
                        onClick = { participants = if (selected) participants - id else participants + id }
                    )
                }
            }
            FieldError(SplitExpenseError.PARTICIPANTS in errors, R.string.split_error_participants)
            SharePreview(state, colors, amountText, participantOptions.filter { it in participants })

            DateRow(
                text = stringResource(R.string.split_expense_date, dateFormat.format(date)),
                onClick = { showDatePicker = true }
            )
            FieldError(SplitExpenseError.DATE in errors, R.string.split_error_date)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (expense != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_cancel)) }
                Button(
                    onClick = {
                        // Se conserva el orden de los miembros del mes para guardar una lista estable.
                        val chosen = participantOptions.filter { it in participants }
                        errors = onSave(title, amountText, payerId, chosen, date, reimbursement)
                        if (errors.isEmpty()) onDismiss()
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(stringResource(R.string.split_save)) }
            }
        }
    }

    if (showDatePicker) {
        SplitDatePickerDialog(
            initial = date,
            // Un reembolso puede saldarse ya entrado el mes siguiente y una actividad no tiene mes: no se limita.
            monthKey = if (reimbursement || month.activity) null else month.monthKey,
            onConfirm = {
                date = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (confirmDelete && expense != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.split_expense_delete_title)) },
            text = { Text(stringResource(R.string.split_expense_delete_message, expense.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}

@Composable
private fun FieldError(visible: Boolean, message: Int) {
    if (visible) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Tarjeta con lo que paga cada participante, en su color, y el resumen «Cada uno: X €» (o el
 * detalle de los céntimos sobrantes, «33,34 € a uno, 33,33 € al resto»). Sirve para comprobar a
 * ojo el reparto antes de guardar.
 */
@Composable
private fun SharePreview(state: SplitUiState, colors: Map<String, Color>, amountText: String, participants: List<String>) {
    val cents = parseCents(amountText) ?: return
    val byMember = splitEqually(cents, participants)
    if (byMember.isEmpty()) return
    val high = byMember.values.max()
    val low = byMember.values.min()
    val summary = if (high == low) {
        stringResource(R.string.split_each, centsText(high))
    } else {
        val withExtra = byMember.values.count { it == high }
        pluralStringResource(R.plurals.split_each_uneven, withExtra, withExtra, centsText(high), centsText(low))
    }
    SpendHeaderCard {
        Text(summary, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        participants.forEach { id ->
            val name = splitMemberName(state, id)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(name, colors.memberColor(id), size = 28.dp)
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                Text(centsText(byMember[id] ?: 0L), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Chip de un miembro: punto de su color y, al elegirlo, fondo y texto de ese color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        leadingIcon = { ColorDot(color) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.22f),
            selectedLabelColor = color
        )
    )
}

/** Fila de la fecha: icono de calendario en su baldosa y la fecha elegida; al pulsarla se abre el calendario. */
@Composable
private fun DateRow(text: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.primary, size = 36.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Calendario limitado a los días de [monthKey] (o sin límite si es null). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitDatePickerDialog(
    initial: Date,
    monthKey: String?,
    onConfirm: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val bounds = remember(monthKey) {
        monthKey?.let { monthBounds(it) }?.let { (first, last) -> localDayToUtcMillis(first)..localDayToUtcMillis(last) }
    }
    val monthYear = monthKey?.take(4)?.toIntOrNull()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = localDayToUtcMillis(initial),
        selectableDates = remember(bounds) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = bounds == null || utcTimeMillis in bounds
                override fun isSelectableYear(year: Int) = monthYear == null || year == monthYear
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitExpenseError
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.expenseCategoryOf
import com.homepantry.app.data.isDateInMonth
import com.homepantry.app.data.localDayToUtcMillis
import com.homepantry.app.data.parseCents
import com.homepantry.app.data.parseShares
import com.homepantry.app.data.sharesRemainderCents
import com.homepantry.app.data.splitEqually
import com.homepantry.app.data.utcMillisToLocalDay
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.EmojiButton
import com.homepantry.app.ui.components.EmojiPickerSheet
import com.homepantry.app.ui.components.IconTile
import com.homepantry.app.ui.components.MemberAvatar
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.centsText
import com.homepantry.app.ui.components.color
import com.homepantry.app.ui.components.icon
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

/** Céntimos al formato que se escribe en los campos ("12,50"), sin símbolo ni miles. */
private fun centsInput(cents: Long): String = String.format(esLocale, "%d,%02d", cents / 100, cents % 100)

/**
 * Alta ([expenseId] null) y edición de un gasto del grupo [monthId], a pantalla completa. El
 * grupo lo mantiene abierto la ruta (RetainSplitMonth), así que los gastos ya están cargados.
 */
@Composable
fun SplitExpenseScreen(viewModel: SplitViewModel, monthId: String, expenseId: String?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val month = state.months.firstOrNull { it.id == monthId }
    val expense = expenseId?.let { id -> state.expenses.firstOrNull { it.id == id } }

    // Todas las salidas pasan por aquí: tras guardar o borrar, el snapshot que llega durante la
    // animación de salida podría volver a disparar «atrás» y sacar también la pantalla del grupo.
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            onBack()
        }
    }

    // Si otro miembro borra el grupo o el gasto mientras se edita, se vuelve atrás.
    val gone = (state.monthsLoaded && month == null) ||
        (expenseId != null && state.openMonthId == monthId && state.expensesLoaded && expense == null)
    LaunchedEffect(gone) { if (gone) leave() }

    if (month == null || (expenseId != null && expense == null)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // La clave reinicia el formulario si cambia el gasto; los snapshots del mismo gasto no lo tocan.
    key(expense?.id) {
        ExpenseForm(
            state = state,
            month = month,
            currentUid = viewModel.currentUid,
            expense = expense,
            onSave = { title, amountText, payerId, participants, date, reimbursement, emoji, shareTexts ->
                viewModel.saveExpense(expense?.id, title, amountText, payerId, participants, date, reimbursement, emoji, shareTexts)
            },
            onDelete = { expense?.let { viewModel.deleteExpense(it.id) } },
            onBack = leave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpenseForm(
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
        reimbursement: Boolean,
        emoji: String,
        shareTexts: Map<String, String>?
    ) -> Set<SplitExpenseError>,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val reimbursement = expense?.reimbursement == true
    var title by remember { mutableStateOf(expense?.title.orEmpty()) }
    var amountText by remember { mutableStateOf(expense?.let { centsInput(it.amountCents) }.orEmpty()) }
    var payerId by remember {
        mutableStateOf(expense?.payerId ?: currentUid.takeIf { it in month.memberIds }.orEmpty())
    }
    var emoji by remember { mutableStateOf(expense?.emoji.orEmpty()) }
    var participants by remember { mutableStateOf(expense?.participants?.toSet() ?: month.memberIds.toSet()) }
    // Un gasto guardado con importes se abre con el interruptor apagado y esos importes.
    var equally by remember { mutableStateOf(expense?.shares.isNullOrEmpty()) }
    var shareTexts by remember {
        mutableStateOf(expense?.shares?.mapValues { centsInput(it.value) } ?: emptyMap())
    }
    var date by remember { mutableStateOf(expense?.date ?: defaultDate(month)) }
    var errors by remember { mutableStateOf(emptySet<SplitExpenseError>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", esLocale) }

    // Al editar un gasto antiguo puede haber participantes que ya no son del mes, o miembros nuevos
    // del mes que no estaban: todos salen como filas para poder ajustarlos.
    val participantOptions = (month.memberIds + (expense?.participants ?: emptyList()) + (expense?.shares?.keys ?: emptySet()))
        .distinct()
    val colors = remember(participantOptions) { memberColors(participantOptions) }
    val category = expenseCategoryOf(title)
    val automaticIcon = if (reimbursement) Icons.Filled.SwapHoriz else category.icon()
    val tint = if (reimbursement) MaterialTheme.colorScheme.onSurfaceVariant else category.color()

    fun setEqually(on: Boolean) {
        if (!on) {
            // Se parte del reparto igual de los marcados para empezar con una suma que ya cuadra.
            val equal = parseCents(amountText)?.let { cents -> splitEqually(cents, participantOptions.filter { it in participants }) }
                ?: emptyMap()
            shareTexts = participantOptions.associateWith { id -> equal[id]?.let(::centsInput).orEmpty() }
        }
        equally = on
    }

    fun save() {
        val manual = !reimbursement && !equally
        // Se guarda en el orden de los miembros del mes para tener una lista estable.
        val chosen = if (manual) participantOptions else participantOptions.filter { it in participants }
        val texts = if (manual) participantOptions.associateWith { shareTexts[it].orEmpty() } else null
        errors = onSave(title, amountText, payerId, chosen, date, reimbursement, emoji, texts)
        if (errors.isEmpty()) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (expense == null) R.string.split_expense_new_title else R.string.split_expense_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    if (expense != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.split_expense_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = ::save) { Text(stringResource(R.string.split_save)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.split_expense_title)) },
                    singleLine = true,
                    isError = SplitExpenseError.TITLE in errors,
                    supportingText = if (SplitExpenseError.TITLE in errors) {
                        { Text(stringResource(R.string.split_error_title)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
                EmojiButton(
                    emoji = if (reimbursement) "" else emoji,
                    automaticIcon = automaticIcon,
                    color = tint,
                    enabled = !reimbursement,
                    onClick = { showEmojiPicker = true },
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

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

            DateRow(
                text = stringResource(R.string.split_expense_date, dateFormat.format(date)),
                onClick = { showDatePicker = true }
            )
            FieldError(SplitExpenseError.DATE in errors, R.string.split_error_date)

            // El reparto va abajo del todo, como en Tricount: primero qué y quién pagó, luego entre quién.
            SpendSectionTitle(stringResource(R.string.split_expense_split_section))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (reimbursement) {
                        // Un reembolso siempre va a un único receptor: se elige con chips, como antes.
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
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clickable { setEqually(!equally) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.split_expense_split_equally),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = equally, onCheckedChange = ::setEqually)
                        }
                        if (equally) {
                            EqualRows(state, colors, amountText, participantOptions, participants) { id ->
                                participants = if (id in participants) participants - id else participants + id
                            }
                        } else {
                            ShareRows(state, colors, participantOptions, shareTexts) { id, text ->
                                shareTexts = shareTexts + (id to text)
                            }
                            SharesStatus(amountText, shareTexts)
                        }
                    }
                }
            }
            FieldError(SplitExpenseError.PARTICIPANTS in errors, R.string.split_error_participants)
            FieldError(SplitExpenseError.SHARES in errors, R.string.split_error_shares)
        }
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            automaticIcon = category.icon(),
            automaticColor = category.color(),
            onPick = {
                emoji = it
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
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
                    onBack()
                }) { Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}

/** Reparto igual: casilla por participante y su parte calculada ("—" mientras el importe no es válido). */
@Composable
private fun EqualRows(
    state: SplitUiState,
    colors: Map<String, Color>,
    amountText: String,
    options: List<String>,
    participants: Set<String>,
    onToggle: (String) -> Unit
) {
    val byMember = parseCents(amountText)?.let { splitEqually(it, options.filter { id -> id in participants }) }
    options.forEach { id ->
        val checked = id in participants
        val name = splitMemberName(state, id)
        Row(
            Modifier.fillMaxWidth().clickable { onToggle(id) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle(id) })
            MemberAvatar(name, colors.memberColor(id), size = 28.dp)
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(
                when {
                    !checked -> ""
                    byMember == null -> "—"
                    else -> centsText(byMember[id] ?: 0L)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Reparto por importes: un campo por participante. En blanco o a 0 = no participa. */
@Composable
private fun ShareRows(
    state: SplitUiState,
    colors: Map<String, Color>,
    options: List<String>,
    shareTexts: Map<String, String>,
    onChange: (id: String, text: String) -> Unit
) {
    options.forEach { id ->
        val name = splitMemberName(state, id)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(name, colors.memberColor(id), size = 28.dp)
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            OutlinedTextField(
                value = shareTexts[id].orEmpty(),
                onValueChange = { onChange(id, it) },
                placeholder = { Text("0,00", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(112.dp)
            )
        }
    }
}

/** Aviso en vivo de lo que falta o sobra para cuadrar con el total; nada si el total o algún importe no se lee. */
@Composable
private fun SharesStatus(amountText: String, shareTexts: Map<String, String>) {
    val total = parseCents(amountText) ?: return
    val shares = parseShares(shareTexts) ?: return
    val remainder = sharesRemainderCents(total, shares)
    val (text, color) = when {
        remainder > 0 -> stringResource(R.string.split_shares_missing, centsText(remainder), centsText(total)) to
            MaterialTheme.colorScheme.error
        remainder < 0 -> stringResource(R.string.split_shares_extra, centsText(-remainder)) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.split_shares_ok) to MaterialTheme.colorScheme.primary
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FieldError(visible: Boolean, message: Int) {
    if (visible) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

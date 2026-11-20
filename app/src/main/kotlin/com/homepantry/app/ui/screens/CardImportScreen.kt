package com.homepantry.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.CardImportRow
import com.homepantry.app.data.CardRowFlag
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.parseCents
import com.homepantry.app.data.toExpenses
import com.homepantry.app.ui.CardImportViewModel
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.GeminiReadingExitDialog
import com.homepantry.app.ui.components.centsText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Revisión de los cargos leídos de las capturas del banco: todos marcados salvo los que
 * probablemente sobran (duplicados, de otro mes, sin fecha), el pagador arriba y «Añadir N
 * gastos» al pie. Se llega desde la barra del grupo con la lectura ya lanzada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardImportScreen(
    importViewModel: CardImportViewModel,
    splitViewModel: SplitViewModel,
    monthId: String,
    apiKeyStore: GeminiApiKeyStore,
    saveExpenses: suspend (String, List<SplitExpense>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val splitState by splitViewModel.state.collectAsState()
    val reading by importViewModel.reading.collectAsState()
    val draft by importViewModel.draft.collectAsState()
    val error by importViewModel.error.collectAsState()
    val saving by importViewModel.saving.collectAsState()
    val month = splitState.months.firstOrNull { it.id == monthId }
    val noKeyMessage = stringResource(R.string.split_import_no_key)

    // El borrador es de este grupo; si es de otro (no debería pasar, la entrada lo impide), no se muestra.
    val ownDraft = draft?.takeIf { it.monthId == monthId }

    var showExitWhileReading by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Todas las salidas pasan por aquí: descartar y la vuelta automática de abajo no deben
    // sacar también la pantalla del grupo.
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            onBack()
        }
    }

    // Tras guardar, el borrador desaparece: se vuelve al grupo. También si no queda nada que hacer
    // (se descartó desde otro sitio o se abrió la ruta sin lectura).
    val nothingLeft = !reading && ownDraft == null && error == null
    LaunchedEffect(nothingLeft) {
        if (nothingLeft) leave()
    }

    val back = {
        when {
            reading -> showExitWhileReading = true
            ownDraft != null -> showDiscard = true
            else -> {
                importViewModel.discard()
                leave()
            }
        }
    }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // El nombre del grupo, porque desde otro grupo se puede llegar a una importación pendiente de este.
                    Column {
                        Text(stringResource(R.string.split_import_title), style = MaterialTheme.typography.titleMedium)
                        month?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        bottomBar = {
            if (ownDraft != null && month != null) {
                val count = ownDraft.toExpenses(month).size
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { importViewModel.save(month) { saveExpenses(monthId, it) } },
                        enabled = count > 0 && !saving,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(pluralStringResource(R.plurals.split_import_add, count, count))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                reading -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    val count = importViewModel.pendingImageCount()
                    Text(
                        pluralStringResource(R.plurals.split_import_reading, count, count),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                ownDraft == null && error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(error.orEmpty(), textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            importViewModel.discard()
                            leave()
                        }) { Text(stringResource(R.string.split_import_discard)) }
                        if (month != null && error != noKeyMessage) {
                            Button(onClick = {
                                importViewModel.retry(
                                    context, month, splitState.expenses, splitViewModel.currentUid, apiKeyStore, noKeyMessage
                                )
                            }) { Text(stringResource(R.string.split_import_retry)) }
                        }
                    }
                }
                ownDraft != null && month != null -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    item {
                        PayerSelector(
                            month = month,
                            payerId = ownDraft.payerId,
                            nameOf = { id ->
                                val name = splitState.memberName(id) ?: stringResource(R.string.split_unknown_member)
                                if (id == splitViewModel.currentUid) stringResource(R.string.split_member_you, name) else name
                            },
                            onSelect = { importViewModel.updateDraft(ownDraft.copy(payerId = it)) }
                        )
                        Text(
                            stringResource(R.string.split_import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (ownDraft.rows.isEmpty()) {
                            Text(
                                stringResource(R.string.split_import_empty),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                            )
                        }
                    }
                    itemsIndexed(ownDraft.rows) { index, row ->
                        ChargeRow(
                            row = row,
                            onToggle = { checked ->
                                val rows = ownDraft.rows.toMutableList()
                                rows[index] = row.copy(selected = checked)
                                importViewModel.updateDraft(ownDraft.copy(rows = rows))
                            },
                            onClick = { editingIndex = index }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val editing = editingIndex?.let { ownDraft?.rows?.getOrNull(it) }
    if (editing != null && month != null && ownDraft != null) {
        EditChargeDialog(
            row = editing,
            month = month,
            onConfirm = { updated ->
                val rows = ownDraft.rows.toMutableList()
                rows[editingIndex!!] = updated
                importViewModel.updateDraft(ownDraft.copy(rows = rows))
                editingIndex = null
            },
            onDismiss = { editingIndex = null }
        )
    }

    if (showExitWhileReading) {
        GeminiReadingExitDialog(
            onExit = {
                showExitWhileReading = false
                leave()
            },
            onDismiss = { showExitWhileReading = false }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.split_import_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    importViewModel.discard()
                    leave()
                }) { Text(stringResource(R.string.split_import_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.split_import_discard_keep)) }
            }
        )
    }
}

@Composable
private fun PayerSelector(month: SplitMonth, payerId: String, nameOf: @Composable (String) -> String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.split_import_payer), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box {
            TextButton(onClick = { open = true }) {
                Text(nameOf(payerId), fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                month.memberIds.forEach { id ->
                    DropdownMenuItem(text = { Text(nameOf(id)) }, onClick = {
                        open = false
                        onSelect(id)
                    })
                }
            }
        }
    }
}

@Composable
private fun ChargeRow(row: CardImportRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES")) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Una fila sin fecha o con el importe mal no se puede añadir: primero hay que editarla.
        Checkbox(checked = row.selected && row.isValid(), onCheckedChange = onToggle, enabled = row.isValid())
        Column(Modifier.weight(1f)) {
            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val flagText = when (row.flag) {
                CardRowFlag.NONE -> null
                CardRowFlag.DUPLICATE -> stringResource(R.string.split_import_flag_duplicate)
                CardRowFlag.OUT_OF_MONTH -> stringResource(R.string.split_import_flag_out_of_month)
                CardRowFlag.NO_DATE -> stringResource(R.string.split_import_flag_no_date)
            }
            val dateText = row.dateMillis?.let { dateFormat.format(Date(it)) }
            listOfNotNull(dateText, flagText).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            parseCents(row.amountText)?.let(::centsText) ?: row.amountText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun EditChargeDialog(row: CardImportRow, month: SplitMonth, onConfirm: (CardImportRow) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(row.title) }
    var amount by rememberSaveable { mutableStateOf(row.amountText) }
    var dateMillis by rememberSaveable { mutableStateOf(row.dateMillis) }
    var picking by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEEE d MMM yyyy", Locale.forLanguageTag("es-ES")) }
    val edited = row.copy(title = title, amountText = amount, dateMillis = dateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_import_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.split_import_concept)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.split_import_amount)) },
                    isError = parseCents(amount) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateMillis?.let { dateFormat.format(Date(it)) } ?: stringResource(R.string.split_import_pick_date))
                }
            }
        },
        confirmButton = {
            // Una fila corregida hasta ser válida se marca: si el usuario la ha arreglado es para añadirla.
            TextButton(
                onClick = { onConfirm(if (edited.isValid() && !row.isValid()) edited.copy(selected = true) else edited) }
            ) { Text(stringResource(R.string.split_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_cancel)) } }
    )

    if (picking) {
        SplitDatePickerDialog(
            initial = dateMillis?.let(::Date) ?: Date(),
            monthKey = if (month.activity) null else month.monthKey,
            onConfirm = {
                dateMillis = it.time
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

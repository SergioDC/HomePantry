package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.EmojiButton
import com.homepantry.app.ui.components.EmojiPickerSheet
import com.homepantry.app.ui.components.MemberAvatar
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.groupColors
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.memberColor
import com.homepantry.app.ui.components.memberColors
import com.homepantry.app.ui.components.splitMemberName
import java.time.YearMonth
import java.util.Locale

private val esLocale = Locale.forLanguageTag("es-ES")

/**
 * «Editar SNcount»: nombre (y el mes, en un grupo de tipo mes), su emoji y añadir participantes.
 * Nunca se quita a nadie: descuadraría los saldos de los gastos que ya tiene. La ruta mantiene el
 * grupo abierto (RetainSplitMonth) para saber si ya tiene gastos.
 */
@Composable
fun SplitMonthEditScreen(viewModel: SplitViewModel, monthId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val month = state.months.firstOrNull { it.id == monthId }

    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            onBack()
        }
    }
    // Si otro miembro borra el grupo mientras se edita, se vuelve atrás.
    val gone = state.monthsLoaded && month == null
    LaunchedEffect(gone) { if (gone) leave() }

    if (month == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    key(month.id) {
        MonthEditForm(
            state = state,
            month = month,
            onSave = { name, monthKey, emoji, added ->
                viewModel.editMonth(month.id, name, monthKey, emoji, added)
                leave()
            },
            onBack = leave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthEditForm(
    state: SplitUiState,
    month: SplitMonth,
    onSave: (name: String, monthKey: String, emoji: String, addedMemberIds: List<String>) -> Unit,
    onBack: () -> Unit
) {
    val initialMonth = remember(month.monthKey) { runCatching { YearMonth.parse(month.monthKey) }.getOrDefault(YearMonth.now()) }
    var yearMonth by remember { mutableStateOf(initialMonth) }
    var name by remember { mutableStateOf(month.name) }
    // Como al crear: mientras el nombre sea el del mes, sigue al mes elegido.
    var nameEdited by remember { mutableStateOf(month.name != splitMonthDisplayName(initialMonth)) }
    var emoji by remember { mutableStateOf(month.emoji) }
    var added by remember { mutableStateOf(emptySet<String>()) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Los gastos de un mes están validados contra ese mes: solo se cambia si aún no tiene ninguno.
    val expensesKnown = state.openMonthId == month.id && state.expensesLoaded
    val canChangeMonth = expensesKnown && state.expenses.isEmpty()
    val color = groupColors(state.months)[month.id] ?: MaterialTheme.colorScheme.primary
    val automaticIcon = month.copy(name = name).icon()

    // Primero los de la casa por nombre y después los uids del grupo que ya no están en la casa.
    val houseIds = state.members.sortedBy { it.name.lowercase(esLocale) }.map { it.id }
    val options = (houseIds + month.memberIds).distinct()
    val colors = remember(options) { memberColors(options) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_month_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val monthKey = if (month.activity) month.monthKey else yearMonth.toString()
                            onSave(name, monthKey, emoji, options.filter { it in added })
                        },
                        enabled = name.isNotBlank()
                    ) { Text(stringResource(R.string.split_save)) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!month.activity) {
                MonthSelector(
                    month = yearMonth,
                    enabled = canChangeMonth,
                    onMonth = {
                        yearMonth = it
                        if (!nameEdited) name = splitMonthDisplayName(it)
                    }
                )
                if (expensesKnown && !canChangeMonth) {
                    Text(
                        stringResource(R.string.split_month_change_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameEdited = true
                    },
                    label = { Text(stringResource(R.string.split_month_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                EmojiButton(
                    emoji = emoji,
                    automaticIcon = automaticIcon,
                    color = color,
                    enabled = true,
                    onClick = { showEmojiPicker = true },
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            SpendSectionTitle(stringResource(R.string.split_month_participants))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    options.forEach { id ->
                        val already = id in month.memberIds
                        val checked = already || id in added
                        val memberName = splitMemberName(state, id)
                        val toggle = { added = if (id in added) added - id else added + id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already, onClick = toggle)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() }, enabled = !already)
                            MemberAvatar(memberName, colors.memberColor(id), size = 28.dp)
                            Text(
                                memberName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.split_month_members_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            automaticIcon = automaticIcon,
            automaticColor = color,
            onPick = {
                emoji = it
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Member
import com.homepantry.app.ui.components.SegmentedToggle
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private val esLocale = Locale.forLanguageTag("es-ES")

/** "Octubre 2026": nombre por defecto de un mes nuevo. "LLLL" es el nombre del mes suelto, no en genitivo. */
internal fun splitMonthDisplayName(month: YearMonth): String {
    val date = Date.from(month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
    return SimpleDateFormat("LLLL yyyy", esLocale).format(date).replaceFirstChar { it.uppercase(esLocale) }
}

/**
 * Alta de un grupo de SNcount: un mes o una actividad, su nombre y los participantes (todos los
 * miembros marcados por defecto; el usuario desmarca, por ejemplo, el miembro duplicado de un
 * móvil reinstalado). En un mes, mientras no se toque el nombre, sigue al mes elegido. Una
 * actividad no tiene mes: se guarda con el actual solo para ordenar la lista.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitMonthFormSheet(
    members: List<Member>,
    unknownMemberName: String,
    onCreate: (name: String, monthKey: String, memberIds: List<String>, activity: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var activity by remember { mutableStateOf(false) }
    var activityName by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var name by remember { mutableStateOf(splitMonthDisplayName(YearMonth.now())) }
    var nameEdited by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(members.map { it.id }.toSet()) }
    val sortedMembers = remember(members) { members.sortedBy { it.name.lowercase(esLocale) } }

    fun changeMonth(newMonth: YearMonth) {
        month = newMonth
        if (!nameEdited) name = splitMonthDisplayName(newMonth)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.split_month_create), style = MaterialTheme.typography.titleLarge)

            SegmentedToggle(
                options = listOf(
                    false to stringResource(R.string.split_kind_month),
                    true to stringResource(R.string.split_kind_activity)
                ),
                selected = activity,
                onSelect = { activity = it }
            )

            if (activity) {
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text(stringResource(R.string.split_month_name)) },
                    placeholder = { Text(stringResource(R.string.split_activity_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                MonthFields(
                    month = month,
                    name = name,
                    onMonth = { changeMonth(it) },
                    onName = {
                        name = it
                        nameEdited = true
                    }
                )
            }

            Text(stringResource(R.string.split_month_participants), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sortedMembers.forEach { member ->
                    val isSelected = member.id in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { selected = if (isSelected) selected - member.id else selected + member.id },
                        label = { Text(member.name.ifBlank { unknownMemberName }) }
                    )
                }
            }
            if (selected.isEmpty()) {
                Text(
                    stringResource(R.string.split_month_participants_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val finalName = if (activity) activityName else name
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_cancel)) }
                Button(
                    onClick = {
                        // Se conserva el orden de la lista de miembros para que el guardado sea estable.
                        val memberIds = sortedMembers.map { it.id }.filter { it in selected }
                        val monthKey = if (activity) YearMonth.now().toString() else month.toString()
                        onCreate(finalName, monthKey, memberIds, activity)
                    },
                    enabled = finalName.isNotBlank() && selected.isNotEmpty(),
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(stringResource(R.string.split_create)) }
            }
        }
    }
}

/** Selector de mes con flechas y nombre del grupo (que por defecto es el del mes). */
@Composable
private fun MonthFields(month: YearMonth, name: String, onMonth: (YearMonth) -> Unit, onName: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.split_month_prev_cd))
            }
            Text(
                splitMonthDisplayName(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.split_month_next_cd))
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.split_month_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

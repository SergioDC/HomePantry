package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.DuplicateMode
import com.homepantry.app.data.weekRangeLabel
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Duplica la semana [source] en otra semana a elegir (por defecto la siguiente). Si la semana
 * destino está vacía copia directamente; si ya tiene entradas pregunta entre reemplazar y combinar.
 * No se puede duplicar una semana sobre sí misma.
 */
@Composable
fun DuplicateWeekDialog(
    source: LocalDate,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var target by remember { mutableStateOf(source.plusWeeks(1)) }
    var conflictCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (conflictCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.menu_duplicate_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.menu_duplicate_message, weekRangeLabel(source)))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { target = target.minusWeeks(1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                        }
                        Text(text = weekRangeLabel(target), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { target = target.plusWeeks(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = target != source && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val count = viewModel.countEntriesInWeek(target)
                            if (count == 0) {
                                viewModel.duplicateWeek(source, target, DuplicateMode.MERGE)
                                onDone()
                            } else {
                                conflictCount = count
                                busy = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.menu_duplicate_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.menu_duplicate_conflict_title)) },
            text = { Text(stringResource(R.string.menu_duplicate_conflict_message, conflictCount)) },
            confirmButton = {
                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
                    TextButton(onClick = {
                        viewModel.duplicateWeek(source, target, DuplicateMode.MERGE)
                        onDone()
                    }) { Text(stringResource(R.string.menu_duplicate_merge)) }
                    TextButton(onClick = {
                        viewModel.duplicateWeek(source, target, DuplicateMode.REPLACE)
                        onDone()
                    }) {
                        Text(
                            stringResource(R.string.menu_duplicate_replace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}

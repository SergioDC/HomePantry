package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.homepantry.app.data.knownPeople
import com.homepantry.app.data.normalizePerson
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.PersonPicker
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Asigna a una persona todas las entradas de [from] a [to] (ambos incluidos): un día o un mes
 * entero, para arreglar de una vez menús que ya estaban metidos sin persona. Por defecto solo toca
 * las que aún no tienen persona, de modo que asignar el mes a Pepe no pisa lo que ya era de Juan; el
 * número de platos que cambiarán se recalcula al cambiar la persona o la casilla.
 */
@Composable
fun AssignPersonDialog(
    scopeLabel: String,
    from: LocalDate,
    to: LocalDate,
    state: MenuUiState,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onDone: (Int) -> Unit
) {
    val names = remember(state.entries, state.people) { knownPeople(state.entries, state.people) }
    var personText by remember { mutableStateOf("") }
    var onlyUnassigned by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val person = normalizePerson(personText, names)

    LaunchedEffect(person, onlyUnassigned) {
        count = null
        count = viewModel.countAssignable(from, to, person, onlyUnassigned)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_assign_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.menu_assign_scope, scopeLabel))
                PersonPicker(
                    personText = personText,
                    onPersonText = { personText = it },
                    names = names,
                    people = state.people
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = onlyUnassigned, onCheckedChange = { onlyUnassigned = it })
                    Text(
                        text = stringResource(R.string.menu_assign_only_unassigned),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                count?.let {
                    Text(
                        text = stringResource(R.string.menu_assign_count, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (count ?: 0) > 0 && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val changed = viewModel.assignPerson(from, to, person, onlyUnassigned)
                        busy = false
                        if (changed != null) onDone(changed) else onDismiss()
                    }
                }
            ) { Text(stringResource(R.string.menu_assign_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
        }
    )
}

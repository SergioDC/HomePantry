package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.dayLabel
import com.homepantry.app.data.entriesFor
import com.homepantry.app.data.groupByPerson
import com.homepantry.app.data.knownPeople
import com.homepantry.app.data.normalizePerson
import com.homepantry.app.data.suggestDishes
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.PersonLabel
import com.homepantry.app.ui.components.PersonPicker
import java.time.LocalDate

/**
 * Hoja de un día del menú, con una pestaña por franja. Se puede añadir un plato escribiendo
 * (con sugerencias de los platos creados) y editar o borrar (con deshacer) los ya puestos.
 * Las entradas salen del estado en vivo, así que reflejan también los cambios de otras personas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMealsSheet(
    date: LocalDate,
    state: MenuUiState,
    viewModel: MenuViewModel,
    onAssignDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var slotIndex by rememberSaveable { mutableIntStateOf(1) }
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MealEntry?>(null) }
    var lastDeleted by remember { mutableStateOf<MealEntry?>(null) }
    // A quién va lo que se añade o se edita; en blanco (o «familia») es toda la familia.
    var personText by remember { mutableStateOf("") }
    val names = remember(state.entries, state.people) { knownPeople(state.entries, state.people) }
    val person = normalizePerson(personText, names)

    val slot = MealSlot.values()[slotIndex]
    val dayEntries = entriesFor(state.entries, date.toString(), slot)
    val suggestions = if (text.isBlank()) emptyList() else suggestDishes(text, state.dishes)

    fun resetInput() {
        // Tras editar se vuelve a Familia; al añadir varios platos seguidos se conserva la persona elegida.
        if (editing != null) personText = ""
        text = ""
        editing = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                // Con el selector de persona la hoja puede no caber en pantallas pequeñas.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = dayLabel(date), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onAssignDay) { Text(stringResource(R.string.menu_assign_day)) }
            }
            TabRow(selectedTabIndex = slotIndex) {
                MealSlot.values().forEachIndexed { index, mealSlot ->
                    Tab(
                        selected = index == slotIndex,
                        onClick = {
                            slotIndex = index
                            resetInput()
                        },
                        text = { Text(mealSlot.label) }
                    )
                }
            }

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                if (dayEntries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.menu_day_empty, slot.label.lowercase()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                groupByPerson(dayEntries).forEach { group ->
                    // El nombre de la persona va una sola vez sobre sus platos, no en cada fila.
                    group.person?.let { groupPerson ->
                        item(key = "person/$groupPerson") {
                            PersonLabel(groupPerson, state.people, Modifier.padding(top = 8.dp))
                        }
                    }
                    items(group.entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editing = entry
                                text = entry.name
                                personText = entry.person.orEmpty()
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.menu_entry_edit_cd))
                            }
                            IconButton(onClick = {
                                viewModel.deleteEntry(entry)
                                lastDeleted = entry
                                if (editing?.id == entry.id) resetInput()
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.menu_entry_delete_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Un Snackbar quedaría tapado por la hoja modal, así que el deshacer va dentro de ella.
            lastDeleted?.let { deleted ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.menu_entry_deleted)}: ${deleted.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        viewModel.restoreEntry(deleted)
                        lastDeleted = null
                    }) { Text(stringResource(R.string.menu_entry_undo)) }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.menu_entry_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            suggestions.forEach { dish ->
                TextButton(onClick = { text = dish.name }) { Text(dish.name) }
            }
            // El selector solo aparece cuando hay algo que añadir o editar, para no recargar la hoja.
            if (text.isNotBlank() || editing != null) {
                Text(text = stringResource(R.string.menu_import_for_whom), style = MaterialTheme.typography.titleSmall)
                PersonPicker(
                    personText = personText,
                    onPersonText = { personText = it },
                    names = names,
                    people = state.people
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (editing != null) {
                    TextButton(onClick = { resetInput() }) {
                        Text(stringResource(R.string.menu_entry_cancel_edit))
                    }
                }
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val target = editing
                        if (target != null) viewModel.editEntry(target, text, person)
                        else viewModel.addEntry(date, slot, text, person)
                        resetInput()
                    }
                ) {
                    Text(
                        stringResource(
                            if (editing != null) R.string.menu_entry_save else R.string.menu_entry_add
                        )
                    )
                }
            }
        }
    }
}

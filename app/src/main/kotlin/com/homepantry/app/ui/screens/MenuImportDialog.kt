package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homepantry.app.R
import com.homepantry.app.data.Dish
import com.homepantry.app.data.MenuImportDraft
import com.homepantry.app.data.MenuImportPlan
import com.homepantry.app.data.Person
import com.homepantry.app.data.ParsedMenu
import com.homepantry.app.data.ParsedMenuDay
import com.homepantry.app.data.cleanParsedMenu
import com.homepantry.app.data.dayLabel
import com.homepantry.app.data.matchDish
import com.homepantry.app.data.monthLabel
import com.homepantry.app.data.normalizePerson
import com.homepantry.app.data.suggestDishes
import com.homepantry.app.data.weekendDayCount
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.PersonPicker
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * Revisión de un menú mensual leído de una foto antes de guardarlo (SPEC 2026-09-21). El mes lo
 * elige el usuario (por defecto el que se estaba viendo; el que Gemini leyó en la cabecera se ofrece como
 * atajo) y los platos se pueden corregir o quitar. Los que no coinciden con ningún plato creado se
 * marcan como nuevos: se crearán al confirmar. Si algún día leído cae en fin de semana en el mes
 * elegido se avisa, porque el menú es de lunes a viernes y lo más probable es que el mes no sea el de
 * la foto.
 *
 * Es un diálogo a pantalla completa y no un bottom sheet: en el sheet, arrastrar la lista o tocar
 * fuera lo cerraba y se perdía la lectura de Gemini, y con el teclado abierto no cabía el botón de
 * importar. Aquí solo se sale con Salir, la X o atrás, y siempre pidiendo confirmación.
 */
@Composable
fun MenuImportDialog(
    draft: MenuImportDraft,
    dishes: List<Dish>,
    names: List<String>,
    people: List<Person>,
    viewModel: MenuViewModel,
    onDraftChange: (MenuImportDraft) -> Unit,
    onDismiss: () -> Unit,
    onDone: (MenuImportPlan) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Se edita en estado local (los campos de texto van mejor sin un StateFlow de por medio) que
    // arranca del borrador del ViewModel, y cada cambio se le pasa con [onDraftChange] para que
    // sobreviva a salir de la pantalla.
    var edited by remember { mutableStateOf(draft) }
    fun edit(value: MenuImportDraft) {
        edited = value
        onDraftChange(value)
    }
    val month = edited.month
    val personText = edited.personText
    val person = normalizePerson(personText, names)
    val days = edited.days
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // (posición del día, posición del plato) del campo enfocado: las sugerencias salen solo bajo ese.
    var focused by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val weekendDays = weekendDayCount(ParsedMenu(null, days), month)
    val importCount = days
        .filter { it.day in 1..month.lengthOfMonth() }
        .sumOf { day -> day.dishes.count { it.isNotBlank() } }

    fun setMonth(value: YearMonth) = edit(edited.copy(month = value))

    fun setDish(dayIndex: Int, dishIndex: Int, text: String) {
        edit(edited.copy(days = days.mapIndexed { i, day ->
            if (i != dayIndex) day
            else day.copy(dishes = day.dishes.mapIndexed { j, dish -> if (j == dishIndex) text else dish })
        }))
    }

    fun removeDish(dayIndex: Int, dishIndex: Int) {
        focused = null
        edit(edited.copy(days = days
            .mapIndexed { i, day ->
                if (i != dayIndex) day else day.copy(dishes = day.dishes.filterIndexed { j, _ -> j != dishIndex })
            }
            .filter { it.dishes.isNotEmpty() }))
    }

    // Mientras se guarda no se puede salir: el guardado ya está en marcha.
    fun requestExit() {
        if (!busy) confirmDiscard = true
    }

    Dialog(
        onDismissRequest = ::requestExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = ::requestExit, enabled = !busy) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.menu_import_close_cd))
                    }
                    Text(text = stringResource(R.string.menu_import_title), style = MaterialTheme.typography.titleLarge)
                }

                // Una sola lista con scroll para todo: con el teclado abierto se encoge y el campo
                // enfocado sigue a la vista, y los botones de abajo no se tapan.
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.menu_import_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { setMonth(month.minusMonths(1)) }) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                                }
                                Text(text = monthLabel(month), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { setMonth(month.plusMonths(1)) }) {
                                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                                }
                            }
                            edited.detectedMonth?.takeIf { it != month }?.let { detected ->
                                TextButton(onClick = { setMonth(detected) }) {
                                    Text(stringResource(R.string.menu_import_use_detected, monthLabel(detected)))
                                }
                            }
                            if (weekendDays > 0) {
                                Text(
                                    text = pluralStringResource(R.plurals.menu_import_weekend_warning, weekendDays, monthLabel(month), weekendDays),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(text = stringResource(R.string.menu_import_for_whom), style = MaterialTheme.typography.titleSmall)
                            PersonPicker(personText = personText, onPersonText = { edit(edited.copy(personText = it)) }, names = names, people = people)
                        }
                    }
                    itemsIndexed(days, key = { _, day -> day.day }) { dayIndex, day ->
                        DayReview(
                            day = day,
                            month = month,
                            dishes = dishes,
                            focusedDish = focused?.takeIf { it.first == dayIndex }?.second,
                            onFocus = { dishIndex -> focused = dayIndex to dishIndex },
                            onChange = { dishIndex, text -> setDish(dayIndex, dishIndex, text) },
                            onRemove = { dishIndex -> removeDish(dayIndex, dishIndex) }
                        )
                    }
                }

                if (failed) {
                    Text(
                        text = stringResource(R.string.menu_import_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = ::requestExit, enabled = !busy) {
                        Text(stringResource(R.string.menu_import_exit))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = importCount > 0 && !busy,
                        onClick = {
                            busy = true
                            failed = false
                            scope.launch {
                                val plan = viewModel.importMenu(cleanParsedMenu(null, days), month, person)
                                busy = false
                                if (plan != null) onDone(plan) else failed = true
                            }
                        }
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.menu_import_confirm, importCount))
                        }
                    }
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.menu_import_discard_title)) },
                text = { Text(stringResource(R.string.menu_import_discard_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.menu_import_discard_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text(stringResource(R.string.menu_import_discard_keep))
                    }
                }
            )
        }
    }
}

/** Un día del menú leído: su fecha y una fila editable por plato, con las sugerencias bajo el campo enfocado. */
@Composable
private fun DayReview(
    day: ParsedMenuDay,
    month: YearMonth,
    dishes: List<Dish>,
    focusedDish: Int?,
    onFocus: (Int) -> Unit,
    onChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (day.day in 1..month.lengthOfMonth()) {
            Text(text = dayLabel(month.atDay(day.day)), style = MaterialTheme.typography.titleSmall)
        } else {
            Text(
                text = stringResource(R.string.menu_import_day_missing, day.day),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        day.dishes.forEachIndexed { dishIndex, name ->
            val isNew = name.isNotBlank() && matchDish(name, dishes) == null
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onChange(dishIndex, it) },
                    singleLine = true,
                    supportingText = if (isNew) ({ Text(stringResource(R.string.menu_import_new_dish)) }) else null,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) onFocus(dishIndex) }
                )
                IconButton(onClick = { onRemove(dishIndex) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.menu_import_remove_dish_cd))
                }
            }
            if (focusedDish == dishIndex && isNew) {
                suggestDishes(name, dishes).forEach { dish ->
                    TextButton(onClick = { onChange(dishIndex, dish.name) }) { Text(dish.name) }
                }
            }
        }
    }
}

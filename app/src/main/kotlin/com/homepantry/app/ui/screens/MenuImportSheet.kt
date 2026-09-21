package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Dish
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
 * elige el usuario (por defecto [defaultMonth]; el que Gemini leyó en la cabecera se ofrece como
 * atajo) y los platos se pueden corregir o quitar. Los que no coinciden con ningún plato creado se
 * marcan como nuevos: se crearán al confirmar. Si algún día leído cae en fin de semana en el mes
 * elegido se avisa, porque el menú es de lunes a viernes y lo más probable es que el mes no sea el de
 * la foto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuImportSheet(
    menu: ParsedMenu,
    defaultMonth: YearMonth,
    dishes: List<Dish>,
    names: List<String>,
    people: List<Person>,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onDone: (MenuImportPlan) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(defaultMonth) }
    // Lo escrito para «Para quién»; en blanco es «sin asignar».
    var personText by remember { mutableStateOf("") }
    val person = normalizePerson(personText, names)
    var days by remember { mutableStateOf(menu.days) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // (posición del día, posición del plato) del campo enfocado: las sugerencias salen solo bajo ese.
    var focused by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val weekendDays = weekendDayCount(ParsedMenu(null, days), month)
    val importCount = days
        .filter { it.day in 1..month.lengthOfMonth() }
        .sumOf { day -> day.dishes.count { it.isNotBlank() } }

    fun setDish(dayIndex: Int, dishIndex: Int, text: String) {
        days = days.mapIndexed { i, day ->
            if (i != dayIndex) day
            else day.copy(dishes = day.dishes.mapIndexed { j, dish -> if (j == dishIndex) text else dish })
        }
    }

    fun removeDish(dayIndex: Int, dishIndex: Int) {
        focused = null
        days = days
            .mapIndexed { i, day ->
                if (i != dayIndex) day else day.copy(dishes = day.dishes.filterIndexed { j, _ -> j != dishIndex })
            }
            .filter { it.dishes.isNotEmpty() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.menu_import_title), style = MaterialTheme.typography.titleLarge)
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
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.menu_prev_cd))
                }
                Text(
                    text = monthLabel(month),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.menu_next_cd))
                }
            }
            menu.month?.takeIf { it != month }?.let { detected ->
                TextButton(onClick = { month = detected }) {
                    Text(stringResource(R.string.menu_import_use_detected, monthLabel(detected)))
                }
            }
            if (weekendDays > 0) {
                Text(
                    text = stringResource(R.string.menu_import_weekend_warning, monthLabel(month), weekendDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(text = stringResource(R.string.menu_import_for_whom), style = MaterialTheme.typography.titleSmall)
            PersonPicker(personText = personText, onPersonText = { personText = it }, names = names, people = people)

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
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
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
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

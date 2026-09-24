package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Dish
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.parseIngredient
import com.homepantry.app.data.Unit as QtyUnit

private data class IngredientRow(val name: String, val qty: String, val unit: String)

private fun blankRow() = IngredientRow(name = "", qty = "", unit = QtyUnit.UD.name)

/**
 * Formulario de un plato (crear o editar): nombre, ingredientes en filas (nombre, cantidad y
 * unidad opcionales) y nota. Las filas sin nombre o con cantidad inválida se limpian al guardar
 * con `parseIngredient`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishFormSheet(initial: Dish?, onDismiss: () -> Unit, onSave: (Dish) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val rows = remember {
        mutableStateListOf<IngredientRow>().apply {
            initial?.ingredients?.forEach { ingredient ->
                add(
                    IngredientRow(
                        name = ingredient.name,
                        qty = ingredient.qty?.let { formatQuantity(it) }.orEmpty(),
                        unit = ingredient.unit ?: QtyUnit.UD.name
                    )
                )
            }
            if (isEmpty()) add(blankRow())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(if (initial == null) R.string.menu_dish_form_new else R.string.menu_dish_form_edit),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.menu_dish_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.menu_dish_ingredients),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            rows.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { rows[index] = row.copy(name = it) },
                        placeholder = { Text(stringResource(R.string.menu_dish_ingredient_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.qty,
                        onValueChange = { rows[index] = row.copy(qty = it) },
                        placeholder = { Text(stringResource(R.string.menu_dish_qty_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(76.dp)
                    )
                    UnitPicker(unit = row.unit, onSelect = { rows[index] = row.copy(unit = it) })
                    IconButton(onClick = {
                        rows.removeAt(index)
                        if (rows.isEmpty()) rows.add(blankRow())
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.menu_dish_remove_ingredient_cd)
                        )
                    }
                }
            }
            TextButton(onClick = { rows.add(blankRow()) }) {
                Text(stringResource(R.string.menu_dish_add_ingredient))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.menu_dish_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Dish(
                            id = initial?.id.orEmpty(),
                            name = name.trim(),
                            ingredients = rows.mapNotNull { parseIngredient(it.name, it.qty, it.unit) },
                            note = note.trim().ifEmpty { null },
                            addedBy = initial?.addedBy,
                            addedAt = initial?.addedAt
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) { Text(stringResource(R.string.menu_dish_save)) }
        }
    }
}

/** Selector de unidad: muestra el nombre corto (ud, kg, g…) y abre un menú con todas. */
@Composable
private fun UnitPicker(unit: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(unit.lowercase()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QtyUnit.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.name)
                    }
                )
            }
        }
    }
}

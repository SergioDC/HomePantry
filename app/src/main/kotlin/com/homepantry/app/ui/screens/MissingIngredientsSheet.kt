package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Ingredient
import com.homepantry.app.data.MissingChoice
import com.homepantry.app.data.PROTECTED_ZONE_NAME
import com.homepantry.app.data.Zone
import com.homepantry.app.data.formatQuantity
import com.homepantry.app.data.zoneDisplayLabel
import com.homepantry.app.data.Unit as QtyUnit

/**
 * «Te faltan estos ingredientes»: todos marcados, cada uno con su zona (por defecto «Otros», o la
 * primera zona si no existe). «Añadir a la lista» entrega solo los marcados con su zona elegida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingIngredientsSheet(
    missing: List<Ingredient>,
    zones: List<Zone>,
    onAdd: (List<MissingChoice>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultZoneId = remember(zones) {
        (zones.firstOrNull { it.name.equals(PROTECTED_ZONE_NAME, ignoreCase = true) } ?: zones.firstOrNull())
            ?.id.orEmpty()
    }
    val checked = remember(missing) { mutableStateListOf<Boolean>().apply { repeat(missing.size) { add(true) } } }
    val zoneIds = remember(missing, defaultZoneId) {
        mutableStateListOf<String>().apply { repeat(missing.size) { add(defaultZoneId) } }
    }
    val selectedCount = checked.count { it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.menu_missing_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.menu_missing_subtitle),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            missing.forEachIndexed { index, ingredient ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                    Column(Modifier.weight(1f)) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        ingredient.qty?.let { qty ->
                            val unitLabel = ingredient.unit?.let { runCatching { QtyUnit.valueOf(it).label }.getOrNull() }
                            Text(
                                text = listOfNotNull(formatQuantity(qty), unitLabel).joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ZonePicker(
                        zones = zones,
                        selectedId = zoneIds[index],
                        onSelect = { zoneIds[index] = it }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_missing_skip)) }
                Button(
                    enabled = selectedCount > 0 && defaultZoneId.isNotEmpty(),
                    onClick = {
                        onAdd(
                            missing.indices
                                .filter { checked[it] }
                                .map { MissingChoice(missing[it], zoneIds[it]) }
                        )
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.menu_missing_add, selectedCount)) }
            }
        }
    }
}

@Composable
private fun ZonePicker(zones: List<Zone>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = zones.firstOrNull { it.id == selectedId }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected?.let { zoneDisplayLabel(it, zones) }.orEmpty())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zoneDisplayLabel(zone, zones)) },
                    onClick = {
                        expanded = false
                        onSelect(zone.id)
                    }
                )
            }
        }
    }
}

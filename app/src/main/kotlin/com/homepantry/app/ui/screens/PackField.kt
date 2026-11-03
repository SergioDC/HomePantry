package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R

/** Unidades de pantalla de un envase; se guardan en kg o l (ver `toPackSize`). */
private val PACK_UNITS = listOf("G", "KG", "ML", "L")

private fun packUnitLabel(unit: String): String = when (unit) {
    "G" -> "g"
    "KG" -> "kg"
    "ML" -> "ml"
    else -> "l"
}

/**
 * Envase de una unidad (cantidad + g/kg/ml/l), para calcular el precio por kilo o litro. Vacío
 * significa "sin envase". Se usa en la revisión del ticket y al editar una compra guardada.
 */
@Composable
fun PackField(
    amountText: String,
    unit: String,
    onChange: (amountText: String, unit: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { onChange(it, unit) },
            label = { Text(stringResource(R.string.receipt_review_pack)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            OutlinedTextField(
                value = packUnitLabel(unit),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.receipt_review_unit)) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.clickable { expanded = true }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PACK_UNITS.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(packUnitLabel(candidate)) },
                        onClick = {
                            onChange(amountText, candidate)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

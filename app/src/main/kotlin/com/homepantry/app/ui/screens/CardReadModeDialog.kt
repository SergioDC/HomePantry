package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.CardReadMode
import com.homepantry.app.data.defaultCardReadMode
import com.homepantry.app.data.needsApiKey

/**
 * Elige cómo se leen las capturas del banco antes de abrir el selector de imágenes. Cada opción
 * dice qué sale del móvil, que es el motivo de poder elegir.
 */
@Composable
fun CardReadModeDialog(
    initialMode: CardReadMode?,
    hasApiKey: Boolean,
    onConfirm: (CardReadMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(defaultCardReadMode(initialMode, hasApiKey)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_read_mode_title)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in CardReadMode.entries) {
                    val enabled = hasApiKey || !mode.needsApiKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == mode,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { selected = mode }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == mode, onClick = null, enabled = enabled)
                        Column(Modifier.padding(start = 12.dp)) {
                            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(stringResource(mode.titleRes()), color = color)
                            Text(
                                stringResource(if (enabled) mode.detailRes() else R.string.split_read_mode_needs_key),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.split_read_mode_pick)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_read_mode_cancel)) }
        }
    )
}

private fun CardReadMode.titleRes(): Int = when (this) {
    CardReadMode.GEMINI_IMAGE -> R.string.split_read_mode_gemini
    CardReadMode.OCR_GEMINI_TEXT -> R.string.split_read_mode_ocr_gemini
    CardReadMode.OCR_ONLY -> R.string.split_read_mode_ocr
}

private fun CardReadMode.detailRes(): Int = when (this) {
    CardReadMode.GEMINI_IMAGE -> R.string.split_read_mode_gemini_detail
    CardReadMode.OCR_GEMINI_TEXT -> R.string.split_read_mode_ocr_gemini_detail
    CardReadMode.OCR_ONLY -> R.string.split_read_mode_ocr_detail
}

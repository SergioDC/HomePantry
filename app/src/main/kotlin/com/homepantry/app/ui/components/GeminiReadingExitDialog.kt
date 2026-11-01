package com.homepantry.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R

/**
 * Aviso al salir de una pantalla mientras Gemini lee un ticket o un menú. La lectura corre en el
 * ViewModel y no se pierde, pero sin aviso parecía que sí: se explica que la revisión espera aquí.
 */
@Composable
fun GeminiReadingExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gemini_reading_exit_title)) },
        text = { Text(stringResource(R.string.gemini_reading_exit_text)) },
        confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.gemini_reading_exit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gemini_reading_exit_keep)) } }
    )
}

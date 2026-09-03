package com.homepantry.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.UserPrefs
import kotlinx.coroutines.launch

/** Permite corregir el nombre local guardado en DataStore (cierre de huecos §5). */
@Composable
fun EditNameDialog(userPrefs: UserPrefs, currentName: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_edit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        scope.launch {
                            userPrefs.saveUserName(trimmed)
                            onDismiss()
                        }
                    }
                }
            ) { Text(stringResource(R.string.main_edit_name_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

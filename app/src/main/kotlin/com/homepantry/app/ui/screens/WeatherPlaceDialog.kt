package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.WeatherPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Elige el pueblo del tiempo del panel con el buscador de Open-Meteo. */
@Composable
fun WeatherPlaceDialog(
    search: suspend (String) -> List<WeatherPlace>,
    onPick: (WeatherPlace) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val runSearch = {
        if (query.isNotBlank() && !searching) {
            searching = true
            failed = false
            scope.launch {
                try {
                    results = search(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = true
                } finally {
                    searching = false
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panel_place_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.panel_place_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    searching -> Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterVertically))
                    }
                    failed -> Text(
                        stringResource(R.string.panel_place_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    results?.isEmpty() == true -> Text(
                        stringResource(R.string.panel_place_none),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    else -> results.orEmpty().forEach { place ->
                        Text(
                            listOf(place.name, place.admin1).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(place) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { runSearch() }) { Text(stringResource(R.string.panel_place_search)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_place_cancel)) }
        }
    )
}

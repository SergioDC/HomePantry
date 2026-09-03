package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.generateHouseholdCode
import com.homepantry.app.data.isValidHouseholdCode
import kotlinx.coroutines.launch

/** SPEC.md sec 1.1: pedir/crear código de casa + nombre, una sola vez. */
@Composable
fun JoinHouseholdScreen(userPrefs: UserPrefs, onJoined: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showCodeError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.join_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.join_subtitle), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.join_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    showCodeError = false
                },
                label = { Text(stringResource(R.string.join_code_label)) },
                isError = showCodeError,
                supportingText = {
                    if (showCodeError) Text(stringResource(R.string.join_code_invalid))
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { code = generateHouseholdCode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.join_create_code))
            }

            Button(
                onClick = {
                    if (!isValidHouseholdCode(code)) {
                        showCodeError = true
                        return@Button
                    }
                    scope.launch {
                        userPrefs.saveUserName(name)
                        userPrefs.saveHousehold(code)
                        onJoined()
                    }
                },
                enabled = name.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.join_continue))
            }
        }
    }
}

package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.BuildConfig
import com.homepantry.app.R
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.validateGeminiApiKey
import com.homepantry.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla "Ajustes": solo datos generales de la casa (código para invitar, miembros,
 * salir de la casa, versión instalada). Gestionar zonas ya no vive aquí -- crear una
 * zona está en el dashboard de Almacén, y renombrar/eliminar en la propia zona.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageZonesScreen(
    viewModel: AppViewModel,
    householdCode: String,
    userName: String,
    userPrefs: UserPrefs,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit,
    onEditName: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.zones_household_code_copied)
    val lastSeenFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "ES")) }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.zones_your_name_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onEditName) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.main_edit_name))
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.zones_household_code_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = householdCode,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(householdCode))
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.zones_household_code_copy_cd))
                        }
                    }
                }
            }
            item {
                GeminiApiKeyCard(
                    geminiApiKeyStore = geminiApiKeyStore,
                    snackbarHostState = snackbarHostState,
                    scope = scope
                )
            }
            item {
                Text(
                    text = stringResource(R.string.zones_members_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(state.members.sortedByDescending { it.lastSeen }, key = { "member/${it.id}" }) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = member.name.ifBlank { "?" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = member.lastSeen?.let { lastSeenFormat.format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                TextButton(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    Text(stringResource(R.string.zones_leave_household), color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                // Para verificar rápido qué build tienes instalada mientras probamos cambios.
                Text(
                    text = stringResource(R.string.zones_build_info, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.zones_leave_household)) },
            text = { Text(stringResource(R.string.zones_leave_household_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    scope.launch { userPrefs.clearHousehold() }
                }) { Text(stringResource(R.string.zones_leave_household), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun GeminiApiKeyCard(
    geminiApiKeyStore: GeminiApiKeyStore,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var storedKey by remember { mutableStateOf<String?>(null) }
    var inputValue by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val savedMessage = stringResource(R.string.zones_gemini_api_key_saved)
    val removedMessage = stringResource(R.string.zones_gemini_api_key_removed)

    LaunchedEffect(Unit) {
        val existing = geminiApiKeyStore.getApiKey()
        storedKey = existing
        inputValue = existing ?: ""
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.zones_gemini_api_key_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.zones_gemini_api_key_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.zones_gemini_api_key_toggle_visibility_cd)
                        )
                    }
                }
            )
            TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
                Text(stringResource(R.string.zones_gemini_api_key_help_link))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (storedKey != null) {
                    TextButton(onClick = {
                        scope.launch {
                            geminiApiKeyStore.clear()
                            storedKey = null
                            inputValue = ""
                            snackbarHostState.showSnackbar(removedMessage)
                        }
                    }) {
                        Text(stringResource(R.string.zones_gemini_api_key_remove))
                    }
                }
                Button(
                    enabled = inputValue.isNotBlank() && !validating,
                    onClick = {
                        val candidate = inputValue.trim()
                        validating = true
                        scope.launch {
                            try {
                                validateGeminiApiKey(candidate)
                                geminiApiKeyStore.save(candidate)
                                storedKey = candidate
                                snackbarHostState.showSnackbar(savedMessage)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Error")
                            } finally {
                                validating = false
                            }
                        }
                    }
                ) {
                    if (validating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.zones_gemini_api_key_save))
                    }
                }
            }
        }
    }
}

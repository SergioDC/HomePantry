package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.NormalizationRow
import com.homepantry.app.data.PackSize
import com.homepantry.app.data.ProductProposal
import com.homepantry.app.data.packDisplay
import com.homepantry.app.data.sortedForReview
import com.homepantry.app.data.toPackSize
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.ProductNormalizationViewModel

/** "300 g", "1,5 l": envase en texto corto para la fila de revisión. */
private fun packLabel(pack: PackSize): String {
    val (amount, unit) = packDisplay(pack)
    return "$amount ${unit.lowercase()}"
}

/**
 * Revisión de los nombres genéricos que se van a poner a las compras antiguas. No se guarda nada
 * hasta pulsar "Aplicar", y las filas desmarcadas no se tocan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductNormalizationScreen(
    viewModel: AppViewModel,
    normalizationViewModel: ProductNormalizationViewModel,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val rows by normalizationViewModel.rows.collectAsState()
    val loading by normalizationViewModel.loading.collectAsState()
    val applying by normalizationViewModel.applying.collectAsState()
    val error by normalizationViewModel.error.collectAsState()
    val resources = LocalResources.current
    var editing by remember { mutableStateOf<NormalizationRow?>(null) }

    fun start() = normalizationViewModel.start(
        state.purchases,
        state.productAliases,
        geminiApiKeyStore,
        resources.getString(R.string.normalize_needs_key)
    )

    // Al entrar se piden las propuestas que falten; si ya había filas (girar, volver) se conservan.
    LaunchedEffect(Unit) { if (rows.isEmpty()) start() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.normalize_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.normalize_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(sortedForReview(rows), key = { it.key }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editing = row },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = row.selected, onCheckedChange = { normalizationViewModel.toggleRow(row.key) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.final.productName + (row.final.pack?.let { " · " + packLabel(it) } ?: ""),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                row.rawName + " · " +
                                    pluralStringResource(R.plurals.normalize_purchase_count, row.purchases.size, row.purchases.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                error?.let { message ->
                    item(key = "error") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = ::start, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.normalize_retry))
                            }
                        }
                    }
                }
                if (!loading && error == null && rows.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.normalize_empty)) }
                }
            }
            HorizontalDivider()
            Button(
                onClick = { normalizationViewModel.apply(viewModel::applyProductUpdates, onBack) },
                enabled = !loading && !applying && rows.any { it.selected },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text(stringResource(R.string.normalize_apply)) }
        }
    }

    editing?.let { row ->
        EditNormalizationDialog(
            row = row,
            onSave = { final ->
                normalizationViewModel.editRow(row.key, final)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun EditNormalizationDialog(row: NormalizationRow, onSave: (ProductProposal) -> Unit, onDismiss: () -> Unit) {
    var name by remember(row) { mutableStateOf(row.final.productName) }
    val initialPack = remember(row) { row.final.pack?.let(::packDisplay) ?: ("" to "G") }
    var packText by remember(row) { mutableStateOf(initialPack.first) }
    var packUnit by remember(row) { mutableStateOf(initialPack.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.normalize_edit_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.receipt_review_printed_name, row.rawName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.receipt_review_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                PackField(
                    amountText = packText,
                    unit = packUnit,
                    onChange = { text, unit ->
                        packText = text
                        packUnit = unit
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(ProductProposal(name.trim(), toPackSize(packText, packUnit))) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.add_item_save_edit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.receipt_review_back)) }
        }
    )
}

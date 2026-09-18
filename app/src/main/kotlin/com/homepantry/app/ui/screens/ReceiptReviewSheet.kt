package com.homepantry.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.ParsedReceiptLine
import com.homepantry.app.ui.AppViewModel
import java.util.Locale

private data class EditableLine(val name: String, val priceText: String)

private fun formatPrice(price: Double): String =
    String.format(Locale("es", "ES"), "%.2f", price)

/**
 * Lista editable de las líneas detectadas por OCR antes de guardarlas.
 * Nada se escribe en Firestore hasta que el usuario pulsa "Guardar
 * compras" (spec "Alcance": revisión obligatoria, sin guardado silencioso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(
    viewModel: AppViewModel,
    initialLines: List<ParsedReceiptLine>,
    ticketPhotoUri: Uri?,
    notice: String? = null,
    onDismiss: () -> Unit
) {
    val lines = remember(initialLines) {
        mutableStateListOf(*initialLines.map { EditableLine(it.name, formatPrice(it.price)) }.toTypedArray())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            item {
                Text(stringResource(R.string.receipt_review_title), style = MaterialTheme.typography.titleLarge)
            }
            // Un Snackbar del Scaffold queda detrás de esta hoja modal y caduca
            // antes de que el usuario la cierre; el aviso va aquí, fijo.
            if (notice != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            itemsIndexed(lines) { index, line ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = line.name,
                        onValueChange = { newValue -> lines[index] = line.copy(name = newValue) },
                        label = { Text(stringResource(R.string.receipt_review_name)) },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = line.priceText,
                        onValueChange = { newValue -> lines[index] = line.copy(priceText = newValue) },
                        label = { Text(stringResource(R.string.receipt_review_price)) },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { lines.removeAt(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.receipt_review_delete_line_cd))
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { lines.add(EditableLine("", "")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.receipt_review_add_line)) }
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.receipt_review_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val parsed = lines.mapNotNull { line ->
                            val price = line.priceText.replace(",", ".").toDoubleOrNull()
                            if (line.name.isBlank() || price == null) null
                            else ParsedReceiptLine(name = line.name.trim(), price = price)
                        }
                        viewModel.savePurchaseBatch(parsed, ticketPhotoUri)
                        onDismiss()
                    },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
                ) { Text(stringResource(R.string.receipt_review_save)) }
            }
        }
    }
}

package com.listacasa.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.Unit as ItemUnit

/**
 * Fila de producto estilo "pill" (Nocturne), reemplaza ProductCard en Lista,
 * Zone Detail y Buscar. `zoneName` (opcional) muestra el chip de etiqueta de
 * zona. `updatedInZoneLabel` (opcional) muestra la nota "Actualizado en
 * {zona}" bajo productos comprados en la vista "Todo". `showDeleteAction`
 * controla el elemento final de la fila: "×" para borrar (Lista/Zone
 * Detail, por defecto) o una flecha ">" (Buscar, donde tocar la fila ya
 * abre la edición y no se ofrece borrar directamente).
 */
@Composable
fun ItemPillRow(
    item: Item,
    zoneName: String? = null,
    updatedInZoneLabel: String? = null,
    showDeleteAction: Boolean = true,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)
    val rowAlpha = if (item.done) 0.5f else 1f

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedIconButton(onClick = onToggleDone, modifier = Modifier.size(28.dp)) {
            if (item.done) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .clickable(onClick = onEdit)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            )
            val addedBySuffix = item.addedBy?.takeIf { it.isNotBlank() }?.let { " · pedido por $it" } ?: ""
            Text(
                text = "${item.qty} $unitLabel$addedBySuffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.note.isNullOrBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!updatedInZoneLabel.isNullOrBlank()) {
                Text(
                    text = updatedInZoneLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!zoneName.isNullOrBlank()) {
            Text(
                text = zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (showDeleteAction) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.main_delete_item_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.listacasa.app.R
import com.listacasa.app.data.Item
import com.listacasa.app.data.Unit as ItemUnit

/** Tocar el nombre/detalles del producto abre el formulario en modo edición (cierre de huecos §1). */
@Composable
fun ProductCard(item: Item, onToggleDone: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}) {
    val alpha = if (item.done) 0.5f else 1f
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)

    Card(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDone) {
                Icon(
                    imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!item.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                )
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
                Text(
                    text = "${item.qty} $unitLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.note.isNullOrBlank()) {
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.addedBy.isNullOrBlank()) {
                    Text(
                        text = "pedido por ${item.addedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.main_delete_item_cd),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

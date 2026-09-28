package com.homepantry.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Item
import com.homepantry.app.data.lightenedZoneColor
import com.homepantry.app.data.Unit as ItemUnit

/** Mismo tono oscuro que `ZoneCard` usa sobre su fondo claro de zona, para mantener el contraste. */
private val PillTextColor = Color(0xFF1C1B1F)

/**
 * Fila de producto estilo "pill" (Nocturne), reemplaza ProductCard en Lista,
 * Zone Detail y Buscar. `zoneName` (opcional) muestra el chip de etiqueta de
 * zona, coloreado con `zoneColor` (hex) si se indica -- así el nombre de la
 * zona se reconoce de un vistazo por su color, igual que en el chip/tarjeta.
 * `rowBackgroundColor` (opcional, hex) tiñe el fondo de toda la fila con la
 * versión clara de ese color (mismo tono que las tarjetas de zona) -- para
 * distinguir productos de zonas/subzonas distintas sin que la lista sea plana.
 * Con ella la fila se convierte en un recuadro: borde del color sin aclarar
 * (más oscuro que el fondo) y un margen vertical que la separa de los
 * elementos vecinos, en vez de tocarlos.
 * `showDeleteAction` controla el elemento final de la fila: "×" para
 * borrar (Lista/Zone Detail, por defecto) o una flecha ">" (Buscar, donde
 * tocar la fila ya abre la edición y no se ofrece borrar directamente).
 */
@Composable
fun ItemPillRow(
    item: Item,
    zoneName: String? = null,
    zoneColor: String? = null,
    rowBackgroundColor: String? = null,
    showDeleteAction: Boolean = true,
    dimWhenDone: Boolean = true,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val unitLabel = runCatching { ItemUnit.valueOf(item.unit).label }.getOrDefault(item.unit)
    // En Zone Detail "done" significa "en stock", no "terminado" -- ahí no se atenúa/tacha
    // (dimWhenDone = false), solo en Lista/Buscar donde sí significa "ya comprado".
    val rowAlpha = if (item.done && dimWhenDone) 0.5f else 1f
    val backgroundColor = rowBackgroundColor
        ?.let { lightenedZoneColor(it) }
        ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
    // Borde sin aclarar: siempre más oscuro que `backgroundColor` (su versión clara).
    val borderColor = rowBackgroundColor
        ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
    // Sobre el fondo claro de la zona el texto pasa a oscuro, igual que en `ZoneCard`.
    val mutedTextColor = if (backgroundColor != null) PillTextColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (backgroundColor != null) Modifier.padding(vertical = 4.dp) else Modifier)
            .then(if (backgroundColor != null) Modifier.background(backgroundColor, MaterialTheme.shapes.small) else Modifier)
            .then(
                if (backgroundColor != null && borderColor != null) {
                    Modifier.border(1.5.dp, borderColor, MaterialTheme.shapes.small)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedIconButton(
            onClick = onToggleDone,
            modifier = Modifier.size(28.dp),
            // Por defecto el aro y el check heredan el color de texto claro (tema oscuro):
            // sobre el fondo aclarado de una zona (Lista y Zone Detail) quedaban invisibles.
            colors = if (backgroundColor != null) {
                IconButtonDefaults.outlinedIconButtonColors(contentColor = PillTextColor)
            } else {
                IconButtonDefaults.outlinedIconButtonColors()
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (backgroundColor != null) PillTextColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
            )
        ) {
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
                color = if (backgroundColor != null) PillTextColor else Color.Unspecified,
                textDecoration = if (item.done && dimWhenDone) TextDecoration.LineThrough else null
            )
            val addedBySuffix = item.addedBy?.takeIf { it.isNotBlank() }?.let { " · pedido por $it" } ?: ""
            Text(
                text = "${item.qty} $unitLabel$addedBySuffix",
                style = MaterialTheme.typography.bodySmall,
                color = mutedTextColor
            )
            if (!item.store.isNullOrBlank()) {
                Text(
                    text = item.store,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!item.note.isNullOrBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = mutedTextColor
                )
            }
        }

        if (!zoneName.isNullOrBlank()) {
            val labelColor = zoneColor
                ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                ?: MaterialTheme.colorScheme.primary
            Text(
                text = zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (showDeleteAction) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.main_delete_item_cd),
                    tint = mutedTextColor
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mutedTextColor,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

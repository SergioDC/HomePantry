package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepantry.app.R

/**
 * Emojis que se ofrecen para un gasto o un grupo. Lista fija y corta, sin librería de emojis:
 * cubre los tipos de gasto de una casa y de los viajes o actividades.
 */
val SPLIT_EMOJIS = listOf(
    "🛒", "🍕", "🍔", "🍝", "🍣", "🥗", "🥐", "🍦",
    "☕", "🍻", "🍷", "🍸", "🎂", "🎉", "🎁", "🎟️",
    "⛽", "🚗", "🚕", "🚌", "🚆", "✈️", "🚲", "🅿️",
    "🏨", "🏖️", "⛰️", "🏕️", "🎿", "⚽", "🎬", "🎮",
    "🏠", "💡", "💧", "🔥", "📶", "📱", "🧹", "🛠️",
    "💊", "🐶", "👕", "📚", "💇", "🎵"
)

private const val EMOJI_COLUMNS = 8

/**
 * Icono de un gasto o grupo que se puede cambiar: el emoji elegido o el icono automático, con un
 * lápiz en la esquina para que se vea que se puede pulsar. Desactivado (sin lápiz) en un reembolso.
 */
@Composable
fun EmojiButton(
    emoji: String,
    automaticIcon: ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.split_emoji_cd)
    Box(
        modifier
            .size(56.dp)
            .semantics { if (enabled) contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        SplitIcon(emoji, automaticIcon, color, Modifier.align(Alignment.Center), size = 52.dp)
        if (enabled) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Hoja para elegir el icono: «Automático» ([onPick] con "") o uno de [SPLIT_EMOJIS]. La cuadrícula
 * se pinta por filas en vez de con LazyVerticalGrid: son pocos y así no hay scroll anidado en la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    automaticIcon: ImageVector,
    automaticColor: Color,
    onPick: (emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.split_emoji_title), style = MaterialTheme.typography.titleLarge)
            Card(onClick = { onPick("") }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(automaticIcon, automaticColor, size = 36.dp)
                    Text(
                        stringResource(R.string.split_emoji_automatic),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            SPLIT_EMOJIS.chunked(EMOJI_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { emoji ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                    // La última fila se rellena para que sus celdas tengan el mismo ancho que las demás.
                    repeat(EMOJI_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

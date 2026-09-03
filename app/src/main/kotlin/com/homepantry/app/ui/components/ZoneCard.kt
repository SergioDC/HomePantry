package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tarjeta de zona del dashboard "Almacén": icono de letra + color auto-derivados. */
@Composable
fun ZoneCard(
    letter: String,
    colorHex: String,
    name: String,
    summaryText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)

    Card(onClick = onClick, modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(dotColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = letter, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

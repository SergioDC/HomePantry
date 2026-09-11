package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.Zone

/**
 * Selección con solo `secondaryContainer` es casi invisible en Nocturne (muy
 * parecido a `surfaceVariant`) -- forzamos `primary` de fondo + borde propio
 * para que el estado seleccionado se note.
 */
@Composable
fun ZoneChip(
    label: String,
    colorHex: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = dotColor?.let {
            {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(it, CircleShape)
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        ),
        modifier = modifier
    )
}

@Composable
fun ZoneChip(zone: Zone, colorHex: String, selected: Boolean, onClick: () -> Unit) {
    ZoneChip(label = zone.name, colorHex = colorHex, selected = selected, onClick = onClick)
}

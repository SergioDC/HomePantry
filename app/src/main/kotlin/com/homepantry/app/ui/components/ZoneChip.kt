package com.listacasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.listacasa.app.data.Zone

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
        colors = FilterChipDefaults.filterChipColors(),
        modifier = modifier
    )
}

@Composable
fun ZoneChip(zone: Zone, colorHex: String, selected: Boolean, onClick: () -> Unit) {
    ZoneChip(label = zone.name, colorHex = colorHex, selected = selected, onClick = onClick)
}

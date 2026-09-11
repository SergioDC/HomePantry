package com.homepantry.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * FAB estilo Nocturne: borde de acento, "+" de acento. Fondo sólido (no transparente):
 * con fondo transparente, el contenido de debajo se veía a través y el botón quedaba
 * poco visible en listas largas.
 */
@Composable
fun NocturneFab(onClick: () -> Unit, contentDescription: String?, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription)
    }
}

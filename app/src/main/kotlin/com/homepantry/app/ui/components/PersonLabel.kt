package com.homepantry.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homepantry.app.ui.theme.Mint400

/**
 * Nombre de la persona a quien va un grupo de platos, en verde menta. Se pone una sola vez delante
 * de sus platos (ver `groupByPerson`) en vez de repetirlo en cada uno. Usa la menta clara de la
 * paleta porque la app es de tema oscuro; la imagen de compartir, de fondo blanco, usa la oscura.
 */
@Composable
fun PersonLabel(person: String, modifier: Modifier = Modifier) {
    Text(
        text = person,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Mint400,
        modifier = modifier.padding(vertical = 2.dp)
    )
}

package com.homepantry.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.Person
import com.homepantry.app.data.colorFor
import com.homepantry.app.ui.theme.Mint400

/**
 * El color elegido para [person] (null = la familia) como `Color` de Compose, en el tono claro que
 * se lee sobre el tema oscuro de la app, o null si nadie ha elegido ninguno.
 */
fun chosenTint(person: String?, people: List<Person>): Color? =
    colorFor(person, people)?.let { Color(it.onDark) }

/**
 * Nombre de la persona a quien va un grupo de platos. Se pone una sola vez delante de sus platos
 * (ver `groupByPerson`) en vez de repetirlo en cada uno. Sin color elegido sale en menta, el de por
 * defecto; la imagen de compartir, de fondo blanco, usa el tono oscuro de cada color.
 */
@Composable
fun PersonLabel(person: String, people: List<Person>, modifier: Modifier = Modifier) {
    Text(
        text = person,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = chosenTint(person, people) ?: Mint400,
        modifier = modifier.padding(vertical = 2.dp)
    )
}

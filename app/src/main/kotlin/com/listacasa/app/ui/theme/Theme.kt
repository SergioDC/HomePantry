package com.listacasa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ListaDeLaCasaColorScheme = lightColorScheme(
    primary = MintPrimary,
    onPrimary = CardColor,
    primaryContainer = Mint100,
    onPrimaryContainer = Ink,
    secondary = LimeSecondary,
    onSecondary = CardColor,
    secondaryContainer = Lime100,
    onSecondaryContainer = Ink,
    error = CoralError,
    onError = CardColor,
    errorContainer = CoralSoft,
    onErrorContainer = Ink,
    background = Background,
    onBackground = Ink,
    surface = CardColor,
    onSurface = Ink,
    surfaceVariant = Mint100,
    onSurfaceVariant = InkSoft,
    outline = Line
)

@Composable
fun ListaDeLaCasaTheme(content: @Composable () -> Unit) {
    // Prototipo web validado usa siempre esquema claro; se mantiene consistente
    // en la app nativa independientemente del tema del sistema.
    MaterialTheme(
        colorScheme = ListaDeLaCasaColorScheme,
        typography = Typography(),
        content = content
    )
}

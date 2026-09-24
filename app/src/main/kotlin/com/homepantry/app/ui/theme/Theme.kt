package com.homepantry.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ListaDeLaCasaColorScheme = darkColorScheme(
    primary = NocturnePrimary,
    onPrimary = NocturneOnPrimary,
    primaryContainer = NocturnePrimaryContainer,
    onPrimaryContainer = NocturneOnPrimaryContainer,
    secondary = NocturneSecondary,
    onSecondary = NocturneOnSecondary,
    secondaryContainer = NocturneSecondaryContainer,
    onSecondaryContainer = NocturneOnSecondaryContainer,
    error = NocturneError,
    onError = NocturneOnError,
    errorContainer = NocturneErrorContainer,
    onErrorContainer = NocturneOnErrorContainer,
    background = NocturneBackground,
    onBackground = NocturneOnSurface,
    surface = NocturneSurface,
    onSurface = NocturneOnSurface,
    surfaceVariant = NocturneSurfaceVariant,
    // ModalBottomSheet usa surfaceContainerLow, AlertDialog surfaceContainerHigh y DropdownMenu surfaceContainer.
    surfaceContainerLow = NocturnePopupSurface,
    surfaceContainer = NocturnePopupSurface,
    surfaceContainerHigh = NocturnePopupSurface,
    onSurfaceVariant = NocturneOnSurfaceVariant,
    outline = NocturneOutline
)

@Composable
fun ListaDeLaCasaTheme(content: @Composable () -> Unit) {
    // Nocturne: la app siempre usa este esquema oscuro, sin seguir el tema
    // del sistema (docs/superpowers/specs/2026-07-20-nocturne-redesign-design.md).
    MaterialTheme(
        colorScheme = ListaDeLaCasaColorScheme,
        typography = Typography(),
        content = content
    )
}

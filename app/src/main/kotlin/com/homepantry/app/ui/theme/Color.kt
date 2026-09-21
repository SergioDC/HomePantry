package com.homepantry.app.ui.theme

import androidx.compose.ui.graphics.Color

// Menta (primario)
val MintPrimary = Color(0xFF0F6E56)
val Mint700 = Color(0xFF1D9E75)
val Mint400 = Color(0xFF5DCAA5)
val Mint200 = Color(0xFF9FE1CB)
val Mint100 = Color(0xFFE1F5EE)

// Lima (segundo acento)
val LimeSecondary = Color(0xFF639922)
val Lime600 = Color(0xFF97C459)
val Lime300 = Color(0xFFC0DD97)
val Lime100 = Color(0xFFEAF3DE)

// Coral (complementario — reservado para alertas y eliminar)
val CoralError = Color(0xFFD85A30)
val CoralSoft = Color(0xFFFAECE7)

// Neutros
val Background = Color(0xFFF4F8F5)
val CardColor = Color(0xFFFFFFFF)
val Ink = Color(0xFF16261F)
val InkSoft = Color(0xFF6E8079)
val Line = Color(0xFFE3EEE7)

// Paleta rotatoria para chips de zona (SPEC.md §6)
val ZoneColors = listOf(
    Mint700,
    LimeSecondary,
    CoralError,
    MintPrimary,
    Lime600,
    Color(0xFF993C1D),
    Mint400
)

// Nocturne (tema oscuro del rediseño de navegación). El Mint/Lime de arriba
// queda sin usar mientras no haya alternancia claro/oscuro.
val NocturneBackground = Color(0xFF161826)
val NocturneSurface = Color(0xFF232532)
val NocturneSurfaceVariant = Color(0xFF2A2C3C)
// Sheets, diálogos y menús desplegables: más claros que el fondo y las tarjetas para que resalten.
val NocturnePopupSurface = Color(0xFF383B54)
val NocturnePrimary = Color(0xFF9184D9)
val NocturneOnPrimary = Color(0xFF161826)
val NocturnePrimaryContainer = Color(0xFF2E2A44)
val NocturneOnPrimaryContainer = Color(0xFFE9E9ED)
val NocturneSecondary = Color(0xFF716A9E)
val NocturneOnSecondary = Color(0xFFE9E9ED)
val NocturneSecondaryContainer = Color(0xFF2E2A44)
val NocturneOnSecondaryContainer = Color(0xFFE9E9ED)
val NocturneError = Color(0xFFD85A30)
val NocturneOnError = Color(0xFF161826)
val NocturneErrorContainer = Color(0xFF4A2A22)
val NocturneOnErrorContainer = Color(0xFFE9E9ED)
val NocturneOnSurface = Color(0xFFE9E9ED)
val NocturneOnSurfaceVariant = Color(0xFFB8B8C4)
val NocturneOutline = Color(0xFFE9E9ED).copy(alpha = 0.16f)

package com.listacasa.app.ui.theme

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

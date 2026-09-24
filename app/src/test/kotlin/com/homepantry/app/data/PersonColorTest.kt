package com.homepantry.app.data

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonColorTest {
    private val darkSurface = 0xFF232532L      // NocturneSurface: tarjetas y fondo de la hoja del menú
    private val popupSurface = 0xFF383B54L     // NocturnePopupSurface: hojas, diálogos y menús
    private val white = 0xFFFFFFFFL            // fondo de la imagen de compartir

    // Contraste WCAG entre dos colores ARGB opacos. Solo lo usa este test, así que vive aquí.
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test fun `the palette has eight distinct colors with mint first, the default`() {
        val colors = PersonColor.values()
        assertEquals(8, colors.size)
        assertEquals(PersonColor.MINT, colors.first())
        assertEquals(colors.size, colors.map { it.label }.distinct().size)
        assertEquals(colors.size, colors.map { it.onDark }.distinct().size)
    }

    @Test fun `every color is readable on the dark app surfaces`() {
        PersonColor.values().forEach { color ->
            assertTrue("${color.name} sobre la tarjeta oscura", contrast(color.onDark, darkSurface) >= 4.5)
            assertTrue("${color.name} sobre la hoja emergente", contrast(color.onDark, popupSurface) >= 4.5)
        }
    }

    @Test fun `every color has a darker tone that is readable on the white share image`() {
        PersonColor.values().forEach { color ->
            assertTrue("${color.name} sobre blanco", contrast(color.onLight, white) >= 4.5)
        }
    }

    @Test fun `the mint tones are the ones the app already used for the person name`() {
        assertEquals(0xFF5DCAA5L, PersonColor.MINT.onDark)   // Mint400
        assertEquals(0xFF0F6E56L, PersonColor.MINT.onLight)  // MintPrimary
    }

    @Test fun `a color is found by its stored id and an unknown or missing id gives none`() {
        assertEquals(PersonColor.CORAL, personColorOf("CORAL"))
        assertNull(personColorOf("FUCSIA"))
        assertNull(personColorOf(null))
    }
}

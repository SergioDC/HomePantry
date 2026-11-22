package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PanelSkyLogicTest {

    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `fase con sol`() {
        val rise = 8 * 60 + 10
        val set = 17 * 60 + 50
        assertEquals(SkyPhase.NIGHT, skyPhase(at(7), rise, set))
        assertEquals(SkyPhase.DAY, skyPhase(at(8, 10), rise, set))
        assertEquals(SkyPhase.DAY, skyPhase(at(16, 19), rise, set))
        assertEquals(SkyPhase.DUSK, skyPhase(at(16, 20), rise, set))
        assertEquals(SkyPhase.DUSK, skyPhase(at(17, 49), rise, set))
        assertEquals(SkyPhase.NIGHT, skyPhase(at(17, 50), rise, set))
        assertEquals(SkyPhase.NIGHT, skyPhase(at(23), rise, set))
    }

    @Test
    fun `fase sin datos usa 7 a 19`() {
        assertEquals(SkyPhase.NIGHT, skyPhase(at(6, 59), null, null))
        assertEquals(SkyPhase.DAY, skyPhase(at(7), null, null))
        assertEquals(SkyPhase.DUSK, skyPhase(at(17, 30), null, null))
        assertEquals(SkyPhase.NIGHT, skyPhase(at(19), null, null))
    }

    @Test
    fun `fase con dia muy corto no lanza`() {
        assertEquals(SkyPhase.DUSK, skyPhase(at(10, 20), 600, 650))
        assertEquals(SkyPhase.NIGHT, skyPhase(at(9, 59), 600, 650))
    }

    @Test
    fun escena() {
        sceneSpec(WeatherKind.CLEAR, SkyPhase.DAY).let {
            assertTrue(it.sun); assertFalse(it.moon); assertEquals(0, it.clouds)
        }
        sceneSpec(WeatherKind.CLEAR, SkyPhase.NIGHT).let {
            assertFalse(it.sun); assertTrue(it.moon); assertTrue(it.stars)
        }
        sceneSpec(WeatherKind.PARTLY_CLOUDY, SkyPhase.DUSK).let {
            assertTrue(it.sun); assertEquals(3, it.clouds); assertEquals(0xFFF6D6CF, it.cloudColor)
        }
        sceneSpec(WeatherKind.CLOUDY, SkyPhase.DAY).let {
            assertFalse(it.sun); assertEquals(7, it.clouds)
        }
        sceneSpec(WeatherKind.RAIN, SkyPhase.DAY).let {
            assertEquals(Precipitation.RAIN, it.precipitation); assertEquals(90, it.particles); assertEquals(6, it.clouds)
        }
        sceneSpec(WeatherKind.DRIZZLE, SkyPhase.DAY).let {
            assertEquals(Precipitation.DRIZZLE, it.precipitation); assertEquals(45, it.particles)
        }
        sceneSpec(WeatherKind.STORM, SkyPhase.NIGHT).let {
            assertEquals(Precipitation.STORM, it.precipitation); assertEquals(130, it.particles)
            assertTrue(it.lightning); assertEquals(7, it.clouds); assertEquals(0xFF2A2E3D, it.cloudColor)
        }
        sceneSpec(WeatherKind.SNOW, SkyPhase.NIGHT).let {
            assertEquals(Precipitation.SNOW, it.precipitation); assertEquals(70, it.particles); assertTrue(it.stars)
        }
        sceneSpec(WeatherKind.FOG, SkyPhase.DAY).let {
            assertTrue(it.fog); assertEquals(2, it.clouds); assertFalse(it.lightning)
        }
    }

    @Test
    fun degradados() {
        assertEquals(listOf(0xFF2F7FD9, 0xFF6FB4F0, 0xFFB9E0FF), skyGradient(WeatherKind.CLEAR, SkyPhase.DAY))
        WeatherKind.entries.forEach { kind ->
            SkyPhase.entries.forEach { phase ->
                val colors = skyGradient(kind, phase)
                assertEquals(3, colors.size)
                colors.forEach { assertEquals(0xFFL, it ushr 24) }
            }
        }
    }

    @Test
    fun saludo() {
        assertEquals(Greeting.MORNING, greeting(at(13, 59)))
        assertEquals(Greeting.AFTERNOON, greeting(at(14)))
        assertEquals(Greeting.AFTERNOON, greeting(at(20, 59)))
        assertEquals(Greeting.NIGHT, greeting(at(21)))
        assertEquals(Greeting.NIGHT, greeting(at(5)))
        assertEquals(Greeting.MORNING, greeting(at(6)))
    }

    @Test
    fun `progreso del ciclo preciso tras dias encendido`() {
        // Unos 4 días en ciclos exactos de 550 ms: con Float el paso ya sería de ~32 ms.
        val fourDays = 550L * 630_000
        assertEquals(0.5f, cycleProgress(fourDays + 275, 550f, 0f), 0.001f)
        assertEquals(0.75f, cycleProgress(fourDays + 275, 550f, 0.25f), 0.001f)
        assertEquals(0f, cycleProgress(0L, 1_000f, 0f), 0f)
    }
}

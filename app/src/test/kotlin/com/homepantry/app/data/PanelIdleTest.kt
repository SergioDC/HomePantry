package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelIdleTest {

    @Test
    fun `modo apagado nunca hace nada`() {
        assertEquals(IdleAction.NONE, idleAction(false, "panel", 10 * 60_000L))
        assertEquals(IdleAction.NONE, idleAction(false, "manageZones", 10 * 60_000L))
    }

    @Test
    fun `antes de 2 minutos nada`() {
        assertEquals(IdleAction.NONE, idleAction(true, "panel", 119_999L))
        assertEquals(IdleAction.NONE, idleAction(true, "manageZones", 119_999L))
    }

    @Test
    fun `en el panel se atenua`() {
        assertEquals(IdleAction.DIM, idleAction(true, "panel", 120_000L))
        assertEquals(IdleAction.DIM, idleAction(true, "panelRestock", 120_000L))
    }

    @Test
    fun `fuera del panel se vuelve`() {
        assertEquals(IdleAction.RETURN_TO_PANEL, idleAction(true, "manageZones", 120_000L))
        assertEquals(IdleAction.RETURN_TO_PANEL, idleAction(true, null, 120_000L))
    }
}

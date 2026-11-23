package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDevicesTest {

    private val kitchenLight = PanelDevice("l1", "cocina", DeviceKind.LIGHT, "Luz", on = false, brightness = 50, color = LightColor.COOL)
    private val livingLight = PanelDevice("l2", "salon", DeviceKind.LIGHT, "Lámpara", on = true, color = LightColor.PINK, supportsColor = true)
    private val kitchenBlind = PanelDevice("c1", "cocina", DeviceKind.COVER, "Persiana", position = 0)
    private val livingBlind = PanelDevice("c2", "salon", DeviceKind.COVER, "Persiana", position = 40)
    private val plug = PanelDevice("s1", "salon", DeviceKind.SWITCH, "Enchufe", on = true)
    private val all = listOf(kitchenLight, livingLight, kitchenBlind, livingBlind, plug)

    @Test
    fun `agrupa por area en orden de aparicion`() {
        val groups = groupByArea(all)
        assertEquals(listOf("cocina", "salon"), groups.map { it.first })
        assertEquals(listOf("l1", "c1"), groups[0].second.map { it.id })
    }

    @Test
    fun `persiana activa si esta algo abierta`() {
        assertFalse(kitchenBlind.isActive())
        assertTrue(livingBlind.isActive())
    }

    @Test
    fun `accion rapida de la persiana sube si esta cerrada y baja si no`() {
        assertEquals(100, kitchenBlind.quickAction().target)
        assertEquals(0, livingBlind.quickAction().target)
        assertTrue(kitchenLight.quickAction().on)
    }

    @Test
    fun `apagar todo no mueve las persianas`() {
        val result = allOff(all)
        assertTrue(result.filter { it.kind != DeviceKind.COVER }.none { it.on })
        assertEquals(40, result.first { it.id == "c2" }.position)
        assertNull(result.first { it.id == "c2" }.target)
    }

    @Test
    fun `cocinar solo toca la cocina`() {
        val result = applyScene(all, PanelScene.COOK, "cocina")
        assertEquals(kitchenLight.copy(on = true, brightness = 100), result[0])
        assertEquals(100, result[2].target)
        assertEquals(livingLight, result[1])
        assertEquals(livingBlind, result[3])
    }

    @Test
    fun `cena pone calido solo a las luces con color`() {
        val result = applyScene(all, PanelScene.DINNER, "cocina")
        assertEquals(LightColor.COOL, result[0].color)
        assertEquals(LightColor.WARM, result[1].color)
        assertTrue(result[0].on && result[1].on)
        assertEquals(35, result[1].brightness)
    }

    @Test
    fun `buenas noches apaga y baja todo`() {
        val result = applyScene(all, PanelScene.NIGHT, "cocina")
        assertTrue(result.filter { it.kind != DeviceKind.COVER }.none { it.on })
        assertEquals(0, result[3].target)
    }

    @Test
    fun `el motor acerca la persiana y se para al llegar`() {
        var list = listOf(livingBlind.copy(target = 45))
        list = stepCovers(list)
        assertEquals(42, list[0].position)
        assertTrue(anyCoverMoving(list))
        list = stepCovers(stepCovers(list))
        assertEquals(45, list[0].position)
        assertNull(list[0].target)
        assertFalse(anyCoverMoving(list))
        assertEquals(list, stepCovers(list))
    }
}

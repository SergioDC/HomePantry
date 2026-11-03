package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PackSizeParsingTest {
    @Test fun `grams at the end`() =
        assertEquals(SplitName("ARANDANO", PackSize(0.3, "KG")), splitPackSize("ARANDANO 300GR"))

    @Test fun `grams with space and lowercase`() =
        assertEquals(SplitName("Arándano", PackSize(0.15, "KG")), splitPackSize("Arándano 150 g"))

    @Test fun `size in the middle`() =
        assertEquals(SplitName("ARANDANO BANDEJA", PackSize(0.3, "KG")), splitPackSize("ARANDANO 300GR BANDEJA"))

    @Test fun `decimal comma litres`() =
        assertEquals(SplitName("ACEITE", PackSize(1.5, "L")), splitPackSize("ACEITE 1,5 L"))

    @Test fun `centilitres and millilitres`() {
        assertEquals(PackSize(0.33, "L"), splitPackSize("COCA COLA 33CL").pack)
        assertEquals(PackSize(0.5, "L"), splitPackSize("ZUMO 500ML").pack)
    }

    @Test fun kilos() = assertEquals(PackSize(1.0, "KG"), splitPackSize("HARINA 1KG").pack)

    @Test fun `multipack multiplies`() =
        assertEquals(SplitName("LECHE ENTERA", PackSize(6.0, "L")), splitPackSize("LECHE ENTERA 6X1L"))

    @Test fun `no size leaves the name`() =
        assertEquals(SplitName("ARAND. CULTIVADO", null), splitPackSize("ARAND. CULTIVADO"))

    @Test fun `number without unit is not a size`() =
        assertEquals(SplitName("PILAS AA 4", null), splitPackSize("PILAS AA 4"))

    @Test fun `alias key ignores size`() =
        assertEquals(aliasKey("ARANDANO 300GR"), aliasKey("Arándanos 150 g"))

    @Test fun `alias key is a valid document id`() {
        assertFalse(aliasKey("PAN 1/2 BARRA")!!.contains("/"))
        assertNull(aliasKey("500G"))
    }

    @Test fun `user pack text`() {
        assertEquals(PackSize(0.3, "KG"), toPackSize("300", "G"))
        assertEquals(PackSize(1.5, "L"), toPackSize("1,5", "L"))
        assertNull(toPackSize("0", "G"))
        assertNull(toPackSize("abc", "G"))
    }

    @Test fun `pack display`() {
        assertEquals("300" to "G", packDisplay(PackSize(0.3, "KG")))
        assertEquals("1,5" to "L", packDisplay(PackSize(1.5, "L")))
        assertEquals("330" to "ML", packDisplay(PackSize(0.33, "L")))
    }
}

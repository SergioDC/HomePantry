package com.homepantry.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMenuRecognizerTest {
    @Test fun `maps a well-formed menu JSON to days and dishes`() {
        val json = """{"month":9,"year":2026,"days":[
            {"day":1,"dishes":["Lentejas","Merluza","Fruta"]},
            {"day":2,"dishes":["Paella","Ensalada"]}
        ]}"""
        val menu = mapGeminiMenuText(json)
        assertEquals(YearMonth.of(2026, 9), menu.month)
        assertEquals(
            listOf(ParsedMenuDay(1, listOf("Lentejas", "Merluza", "Fruta")), ParsedMenuDay(2, listOf("Paella", "Ensalada"))),
            menu.days
        )
    }

    @Test fun `month is null when the header was not read`() {
        assertNull(mapGeminiMenuText("""{"days":[{"day":1,"dishes":["Lentejas"]}]}""").month)
    }

    @Test fun `month outside 1 to 12 is ignored`() {
        assertNull(mapGeminiMenuText("""{"month":13,"year":2026,"days":[]}""").month)
        assertNull(mapGeminiMenuText("""{"month":0,"year":2026,"days":[]}""").month)
    }

    @Test fun `month without a plausible year is ignored`() {
        assertNull(mapGeminiMenuText("""{"month":9,"days":[]}""").month)
        assertNull(mapGeminiMenuText("""{"month":9,"year":26,"days":[]}""").month)
    }

    @Test fun `entries missing the day or the dishes are skipped instead of crashing`() {
        val json = """{"days":[
            {"day":1,"dishes":["Lentejas"]},
            {"dishes":["Sin día"]},
            {"day":3,"dishes":null},
            {"day":4,"dishes":["Paella",null,"Fruta"]}
        ]}"""
        assertEquals(
            listOf(ParsedMenuDay(1, listOf("Lentejas")), ParsedMenuDay(4, listOf("Paella", "Fruta"))),
            mapGeminiMenuText(json).days
        )
    }

    @Test fun `the result is cleaned, so bread and ALL CAPS are handled`() {
        val menu = mapGeminiMenuText("""{"days":[{"day":1,"dishes":["LENTEJAS","Pan","Fruta"]}]}""")
        assertEquals(listOf("Lentejas", "Fruta"), menu.days.single().dishes)
    }

    @Test fun `an empty days list gives an empty menu`() {
        assertTrue(mapGeminiMenuText("""{"days":[]}""").days.isEmpty())
    }

    @Test fun `throws a response exception on broken JSON`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiMenuText("no es json {") }
    }

    @Test fun `throws a response exception when days is missing`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiMenuText("""{"month":9,"year":2026}""") }
    }
}

package com.homepantry.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuImportDraftTest {

    @Test fun `draft survives a round trip through json`() {
        val draft = MenuImportDraft(
            detectedMonth = YearMonth.of(2026, 10),
            month = YearMonth.of(2026, 11),
            personText = "Lucía",
            days = listOf(ParsedMenuDay(3, listOf("Lentejas", "Merluza")), ParsedMenuDay(4, listOf("Paella")))
        )
        assertEquals(draft, menuImportDraftFromJson(menuImportDraftToJson(draft)))
    }

    @Test fun `draft without detected month keeps it null`() {
        val draft = MenuImportDraft(null, YearMonth.of(2026, 11), "", listOf(ParsedMenuDay(1, listOf("Sopa"))))
        assertEquals(draft, menuImportDraftFromJson(menuImportDraftToJson(draft)))
    }

    @Test fun `broken or empty json gives no draft`() {
        assertNull(menuImportDraftFromJson("{\"month\":\"2026-1"))
        assertNull(menuImportDraftFromJson("{\"month\":\"nope\",\"days\":[{\"day\":1,\"dishes\":[\"Sopa\"]}]}"))
        assertNull(menuImportDraftFromJson("{\"month\":\"2026-11\",\"days\":[]}"))
        assertNull(menuImportDraftFromJson(""))
    }
}

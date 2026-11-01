package com.homepantry.app.data

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptScanDraftTest {

    private val draft = ReceiptScanDraft(
        store = "Eroski",
        dateMillis = 1_793_000_000_000L,
        detectedDateMillis = 1_792_000_000_000L,
        lines = listOf(
            ReceiptDraftLine("Leche", "6", Unit.UD.name, "5,34"),
            ReceiptDraftLine("Plátanos", "0,85", Unit.KG.name, "1,70")
        ),
        photoUri = "content://com.homepantry.app.fileprovider/receipts/1.jpg",
        notice = "Gemini falló: se usó el OCR clásico"
    )

    @Test fun `el borrador sobrevive a ida y vuelta por json`() =
        assertEquals(draft, receiptScanDraftFromJson(receiptScanDraftToJson(draft)))

    @Test fun `sin fecha ni foto ni lineas sigue siendo un borrador`() {
        val empty = ReceiptScanDraft(store = "", dateMillis = null, lines = emptyList(), photoUri = null, notice = null)
        assertEquals(empty, receiptScanDraftFromJson(receiptScanDraftToJson(empty)))
    }

    @Test fun `json roto no da borrador`() {
        assertNull(receiptScanDraftFromJson("{\"store\":\"Ero"))
        assertNull(receiptScanDraftFromJson(""))
    }

    @Test fun `campos que faltan en el json no quedan nulos`() {
        val restored = receiptScanDraftFromJson("{\"lines\":[{\"name\":\"Pan\"}]}")
        assertEquals(ReceiptScanDraft(lines = listOf(ReceiptDraftLine(name = "Pan"))), restored)
        assertEquals("", restored!!.lines.single().priceText)
    }

    @Test fun `se crea desde lo leido con cantidades y precios en formato espanol`() {
        val parsed = ParsedReceipt(
            store = "Lidl",
            lines = listOf(ParsedReceiptLine("Queso", 3.9), ParsedReceiptLine("Manzanas", 2.1, 1.25, Unit.KG.name)),
            date = Date(1_793_000_000_000L)
        )
        assertEquals(
            ReceiptScanDraft(
                store = "Lidl",
                dateMillis = 1_793_000_000_000L,
                detectedDateMillis = 1_793_000_000_000L,
                lines = listOf(
                    ReceiptDraftLine("Queso", "1", Unit.UD.name, "3,90"),
                    ReceiptDraftLine("Manzanas", "1,25", Unit.KG.name, "2,10")
                ),
                photoUri = "content://foto",
                notice = null
            ),
            receiptScanDraft(parsed, "content://foto", null)
        )
    }

    @Test fun `al guardar se descartan las lineas sin nombre y la cantidad ilegible cuenta como 1`() {
        val toSave = ReceiptScanDraft(
            store = "  ",
            dateMillis = 1_793_000_000_000L,
            lines = listOf(
                ReceiptDraftLine(" Pan ", "abc", Unit.UD.name, "1,20"),
                ReceiptDraftLine("", "1", Unit.UD.name, "9,99")
            )
        )
        assertEquals(
            ParsedReceipt(store = null, lines = listOf(ParsedReceiptLine("Pan", 1.2, 1.0, Unit.UD.name)), date = Date(1_793_000_000_000L)),
            toSave.toParsedReceipt()
        )
    }
}

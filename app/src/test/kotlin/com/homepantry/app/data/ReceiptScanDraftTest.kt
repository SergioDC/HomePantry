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
                    ReceiptDraftLine("Queso", "1", Unit.UD.name, "3,90", productName = "Queso", proposedProductName = "Queso"),
                    ReceiptDraftLine("Manzanas", "1,25", Unit.KG.name, "2,10", productName = "Manzanas", proposedProductName = "Manzanas")
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
            ParsedReceipt(store = null, lines = listOf(ParsedReceiptLine("Pan", 1.2, 1.0, Unit.UD.name, productName = "Pan")), date = Date(1_793_000_000_000L)),
            toSave.toParsedReceipt()
        )
    }

    @Test fun `ocr line gets name and pack from rules`() {
        val line = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("ARANDANO 300GR", 2.5))), null, null).lines.single()
        assertEquals("ARANDANO", line.productName)
        assertEquals("300", line.packText)
        assertEquals("G", line.packUnit)
        assertEquals("ARANDANO", line.proposedProductName)
    }

    @Test fun `alias applies when building the draft`() {
        val key = aliasKey("ARAND. CULTIVADO")!!
        val aliases = mapOf(key to ProductAlias(id = key, productName = "Arándanos"))
        val parsed = ParsedReceipt(null, listOf(ParsedReceiptLine("ARAND. CULTIVADO", 2.0, productName = "Arándano cultivado")))
        val line = receiptScanDraft(parsed, null, null, aliases).lines.single()
        assertEquals("Arándanos", line.productName)
        assertEquals("Arándanos", line.proposedProductName)
    }

    @Test fun `edited name produces an alias, untouched does not`() {
        val built = receiptScanDraft(
            ParsedReceipt(null, listOf(ParsedReceiptLine("ARANDANO 300GR", 2.5), ParsedReceiptLine("PAN", 1.0))), null, null
        )
        val edited = built.copy(lines = built.lines.mapIndexed { i, l -> if (i == 0) l.copy(productName = "Arándanos") else l })
        assertEquals(listOf("Arándanos"), edited.aliasesToSave().map { it.productName })
    }

    @Test fun `saved line carries generic name and pack`() {
        val built = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("ARANDANO 300GR", 2.5))), null, null)
        val line = built.toParsedReceipt().lines.single()
        assertEquals("ARANDANO", line.productName)
        assertEquals(PackSize(0.3, "KG"), line.pack)
    }

    @Test fun `pack is ignored for weighed lines`() {
        val built = receiptScanDraft(
            ParsedReceipt(null, listOf(ParsedReceiptLine("PLATANO 300GR", 0.9, quantity = 0.45, unit = Unit.KG.name))), null, null
        )
        assertNull(built.toParsedReceipt().lines.single().pack)
    }

    @Test fun `old draft json without new fields loads with defaults`() {
        val line = receiptScanDraftFromJson("{\"lines\":[{\"name\":\"Pan\",\"qtyText\":\"1\",\"unit\":\"UD\",\"priceText\":\"1,00\"}]}")!!.lines.single()
        assertEquals("", line.productName)
        assertEquals("G", line.packUnit)
        assertEquals("", line.packText)
    }

    @Test fun `gemini failure is remembered in the draft for retrying`() {
        val failed = receiptScanDraft(ParsedReceipt(null, emptyList()), "content://foto", "falló", geminiFailed = true)
        assertEquals(true, failed.geminiFailed)
        assertEquals(true, receiptScanDraftFromJson(receiptScanDraftToJson(failed))!!.geminiFailed)
        assertEquals(false, receiptScanDraftFromJson("{\"lines\":[]}")!!.geminiFailed)
    }

    @Test fun `a scanned line whose name is only a size is not a manual line`() {
        val line = receiptScanDraft(ParsedReceipt(null, listOf(ParsedReceiptLine("500G", 1.0))), null, null).lines.single()
        assertEquals(false, line.manual)
    }

    @Test fun `manual lines do not create aliases`() {
        val draft = ReceiptScanDraft(lines = listOf(ReceiptDraftLine("Pan", "1", Unit.UD.name, "1,00", productName = "Pan", manual = true)))
        assertEquals(emptyList<ProductAlias>(), draft.aliasesToSave())
    }
}

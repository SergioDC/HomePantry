package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ReceiptDateTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun date(s: String) = format.parse(s)!!

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun at(s: String) = timeFormat.parse(s)!!

    private val today = at("2026-10-20 18:30:00")

    @Test fun `parseIsoReceiptDate reads a YYYY-MM-DD date`() {
        assertEquals(date("2026-10-18"), parseIsoReceiptDate("2026-10-18"))
        assertEquals(date("2026-10-18"), parseIsoReceiptDate("  2026-10-18 "))
    }

    @Test fun `parseIsoReceiptDate rejects blanks, other formats and impossible dates`() {
        assertNull(parseIsoReceiptDate(null))
        assertNull(parseIsoReceiptDate(""))
        assertNull(parseIsoReceiptDate("18/10/2026"))
        assertNull(parseIsoReceiptDate("2026-10-18T10:00"))
        assertNull(parseIsoReceiptDate("2026-02-30"))
        assertNull(parseIsoReceiptDate("2026-13-01"))
    }

    @Test fun `plausibleReceiptDate keeps today and recent days`() {
        assertEquals(date("2026-10-20"), plausibleReceiptDate(date("2026-10-20"), today))
        assertEquals(date("2025-11-02"), plausibleReceiptDate(date("2025-11-02"), today))
    }

    @Test fun `plausibleReceiptDate drops future dates and dates older than two years`() {
        assertNull(plausibleReceiptDate(date("2026-10-21"), today))
        assertNull(plausibleReceiptDate(date("2024-10-19"), today))
        assertNull(plausibleReceiptDate(null, today))
    }

    @Test fun `detectReceiptDate finds Mercadona, Lidl and Aldi style dates`() {
        assertEquals(date("2026-10-18"), detectReceiptDate(listOf("MERCADONA S.A.", "18/10/2026 19:42  OP: 123456"), today))
        assertEquals(date("2026-10-04"), detectReceiptDate(listOf("LIDL", "04.10.26 12:01"), today))
        assertEquals(date("2026-09-30"), detectReceiptDate(listOf("ALDI", "Fecha: 30-09-2026"), today))
    }

    @Test fun `detectReceiptDate ignores prices, phone numbers and implausible dates`() {
        assertNull(detectReceiptDate(listOf("TOMATE RAMA 1,50", "TOTAL 12,40", "TEL 96-123-45-67"), today))
        assertNull(detectReceiptDate(listOf("CADUCA 31/12/2027"), today))
        assertNull(detectReceiptDate(listOf("32/10/2026"), today))
        assertNull(detectReceiptDate(emptyList(), today))
    }

    @Test fun `detectReceiptDate skips an implausible date and takes the next one`() {
        assertEquals(
            date("2026-10-18"),
            detectReceiptDate(listOf("PROMO HASTA 31/12/2027", "18/10/2026 19:42"), today)
        )
    }

    @Test fun `date picker millis round-trip keeps the local day`() {
        val millis = localDayToUtcMillis(at("2026-10-18 23:45:00"))
        assertEquals(0L, millis % (24 * 60 * 60 * 1000L))
        assertEquals(date("2026-10-18"), utcMillisToLocalDay(millis))
    }

    @Test fun `ticketTimestamp keeps the chosen day with the current time of day`() {
        assertEquals(at("2026-10-18 18:30:00"), ticketTimestamp(date("2026-10-18"), today))
    }
}

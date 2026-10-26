package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitLinkTest {

    @Test fun `share url round trips`() {
        assertEquals("AbC123", splitMonthIdFromLink(splitShareUrl("AbC123")))
    }

    @Test fun `trailing slash query and fragment are ignored`() {
        assertEquals("AbC123", splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/AbC123/"))
        assertEquals("AbC123", splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/AbC123?utm=wa#x"))
    }

    @Test fun `other links are rejected`() {
        assertNull(splitMonthIdFromLink(null))
        assertNull(splitMonthIdFromLink("https://example.com/sncount/AbC123"))
        assertNull(splitMonthIdFromLink("http://home-pantry-bed42.web.app/sncount/AbC123"))
        assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/"))
        assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/a/b"))
        assertNull(splitMonthIdFromLink("no es una url"))
    }

    @Test fun `ids with characters that break a route are rejected`() {
        // El path llega decodificado: %23 sería un # dentro de la ruta de navegación y la tumbaría.
        assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/%23x"))
        assertNull(splitMonthIdFromLink("https://home-pantry-bed42.web.app/sncount/a%3Fb"))
    }
}

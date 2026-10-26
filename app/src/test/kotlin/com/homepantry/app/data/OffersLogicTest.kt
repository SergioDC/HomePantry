package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

class OffersLogicTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, madrid)

    private fun notification(
        id: String,
        readBy: List<String> = emptyList(),
        type: String = NOTIFICATION_TYPE_OFFERS,
        createdAt: ZonedDateTime = at(2026, 10, 26, 8),
    ) = AppNotification(id = id, type = type, readBy = readBy, createdAt = Date.from(createdAt.toInstant()))

    @Test fun `unreadCount counts notifications the user has not read`() {
        val list = listOf(notification("a"), notification("b", readBy = listOf("u1")), notification("c"))
        assertEquals(2, unreadCount(list, "u1"))
    }

    @Test fun `nextMondayCheck goes to the next monday at 11`() {
        assertEquals(at(2026, 11, 2, 11), nextMondayCheck(at(2026, 10, 28, 10)))
    }

    @Test fun `nextMondayCheck on monday before 11 is the same day`() {
        assertEquals(at(2026, 10, 26, 11), nextMondayCheck(at(2026, 10, 26, 9)))
    }

    @Test fun `nextMondayCheck is strictly after now`() {
        assertEquals(at(2026, 11, 2, 11), nextMondayCheck(at(2026, 10, 26, 11)))
    }

    @Test fun `retryOrReschedule retries in 3 hours inside the window`() {
        assertEquals(at(2026, 10, 26, 14), retryOrReschedule(at(2026, 10, 26, 11)))
    }

    @Test fun `retryOrReschedule leaving the window goes to next monday`() {
        assertEquals(at(2026, 11, 2, 11), retryOrReschedule(at(2026, 10, 27, 22)))
        assertEquals(at(2026, 11, 2, 11), retryOrReschedule(at(2026, 10, 29, 12)))
    }

    @Test fun `initialOffersCheck inside the window is now`() {
        val now = at(2026, 10, 27, 15)
        assertEquals(now, initialOffersCheck(now))
    }

    @Test fun `initialOffersCheck outside the window is next monday`() {
        assertEquals(at(2026, 10, 26, 11), initialOffersCheck(at(2026, 10, 25, 12)))
    }

    @Test fun `pendingOfferNotification ignores read, notified, old and other types`() {
        val now = at(2026, 10, 27, 12).toInstant()
        val list = listOf(
            notification("read", readBy = listOf("u1")),
            notification("notified"),
            notification("old", createdAt = at(2026, 10, 19, 8)),
            notification("other", type = "otro"),
        )
        assertNull(pendingOfferNotification(list, "u1", setOf("notified"), now))
    }

    @Test fun `pendingOfferNotification returns the most recent valid one`() {
        val now = at(2026, 10, 27, 12).toInstant()
        val list = listOf(
            notification("older", createdAt = at(2026, 10, 22, 8)),
            notification("newer", createdAt = at(2026, 10, 26, 8)),
        )
        assertEquals("newer", pendingOfferNotification(list, "u1", emptySet(), now)?.id)
    }

    private val tomato = OfferMatch(key = "lidl-p7-tomate", habitual = "tomate", habitualLabel = "Tomate", product = "Tomate rama")

    @Test fun `offerRowState is ADDED when its key was added`() {
        assertEquals(OfferRowState.ADDED, offerRowState(tomato, listOf("lidl-p7-tomate"), emptyList()))
    }

    @Test fun `offerRowState is IN_LIST when a pending item matches`() {
        val items = listOf(Item(name = "Tomates", done = false))
        assertEquals(OfferRowState.IN_LIST, offerRowState(tomato, emptyList(), items))
    }

    @Test fun `offerRowState is NEW when the matching item is in the pantry`() {
        val items = listOf(Item(name = "Tomate", done = true))
        assertEquals(OfferRowState.NEW, offerRowState(tomato, emptyList(), items))
    }

    @Test fun `offerNote with old price`() {
        val offer = OfferMatch(price = 2.49, oldPrice = 2.85, validUntil = "2026-11-01")
        assertEquals("Oferta 2,49 € (antes 2,85 €) hasta 1/11", offerNote(offer))
    }

    @Test fun `offerNote without old price`() {
        assertEquals("Oferta 2,49 € hasta 1/11", offerNote(OfferMatch(price = 2.49, validUntil = "2026-11-01")))
    }

    @Test fun `offerNote without price points to the leaflet page`() {
        assertEquals("Oferta en el folleto, pág. 7", offerNote(OfferMatch(page = 7)))
    }

    private val zones = listOf(
        Zone(id = "sub", name = "Cajón", parentZoneId = "nevera"),
        Zone(id = "despensa", name = "Despensa"),
        Zone(id = "nevera", name = "Nevera"),
    )

    @Test fun `planOfferAdds creates a new item in the first root zone`() {
        val plan = planOfferAdds(listOf("Lidl" to tomato.copy(price = 1.59)), emptyList(), zones, "Ana")
        assertEquals(
            listOf(Item(name = "Tomate", qty = 1.0, unit = Unit.UD.name, note = "Oferta 1,59 €", zone = "despensa", store = "Lidl", done = false, addedBy = "Ana")),
            plan.newItems,
        )
        assertEquals(emptyList<String>(), plan.reactivateIds)
    }

    @Test fun `planOfferAdds reuses the zone of an item with the same name`() {
        val cheese = OfferMatch(key = "aldi-1", habitual = "queso", habitualLabel = "Queso")
        val items = listOf(Item(id = "x", name = "QUESOS", zone = "nevera", done = false))
        assertEquals("nevera", planOfferAdds(listOf("Aldi" to cheese), items, zones, "Ana").newItems.single().zone)
    }

    @Test fun `isTrustedOfferUrl accepts https links of the stores`() {
        assertEquals(true, isTrustedOfferUrl("https://www.lidl.es/l/folletos/folleto-alimentacion-x/ar/0"))
        assertEquals(true, isTrustedOfferUrl("https://www.aldi.es/ofertas.html"))
    }

    @Test fun `isTrustedOfferUrl rejects other schemes and lookalike hosts`() {
        assertEquals(false, isTrustedOfferUrl("http://www.lidl.es/"))
        assertEquals(false, isTrustedOfferUrl("intent://x#Intent;end"))
        assertEquals(false, isTrustedOfferUrl("https://lidl.es.evil.com/"))
        assertEquals(false, isTrustedOfferUrl("https://evillidl.es/"))
        assertEquals(false, isTrustedOfferUrl(""))
    }

    @Test fun `isValidNotificationId accepts Firestore ids`() {
        assertEquals(true, isValidNotificationId("aB3dE5fG7hJ9kL1mN2pQ"))
    }

    @Test fun `isValidNotificationId rejects route injection and empty ids`() {
        assertEquals(false, isValidNotificationId("x/../home"))
        assertEquals(false, isValidNotificationId("a?b=c"))
        assertEquals(false, isValidNotificationId(""))
        assertEquals(false, isValidNotificationId("a".repeat(41)))
    }

    @Test fun `planOfferAdds moves a pantry item back to the list instead of duplicating it`() {
        val items = listOf(Item(id = "t1", name = "Tomate", zone = "nevera", done = true))
        val plan = planOfferAdds(listOf("Lidl" to tomato), items, zones, "Ana")
        assertEquals(emptyList<Item>(), plan.newItems)
        assertEquals(listOf("t1"), plan.reactivateIds)
    }
}

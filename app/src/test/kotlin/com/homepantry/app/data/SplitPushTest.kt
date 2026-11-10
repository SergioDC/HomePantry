package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitPushTest {

    @Test fun `closed push with author`() {
        val push = parseSplitPush(
            mapOf("type" to "sncount_closed", "monthId" to "AbC123", "monthName" to " Octubre ", "byName" to "Ana")
        )
        assertEquals(SplitPush.Closed("AbC123", "Octubre", "Ana"), push)
    }

    @Test fun `closed push without author keeps an empty name`() {
        val push = parseSplitPush(mapOf("type" to "sncount_closed", "monthId" to "AbC123", "monthName" to "Octubre"))
        assertEquals(SplitPush.Closed("AbC123", "Octubre", ""), push)
    }

    @Test fun `reminder push ignores byName`() {
        val push = parseSplitPush(
            mapOf("type" to "sncount_reminder", "monthId" to "AbC123", "monthName" to "Octubre", "byName" to "Ana")
        )
        assertEquals(SplitPush.Reminder("AbC123", "Octubre"), push)
    }

    @Test fun `missing month name is allowed`() {
        assertEquals(SplitPush.Reminder("AbC123", ""), parseSplitPush(mapOf("type" to "sncount_reminder", "monthId" to "AbC123")))
    }

    @Test fun `unknown type or invalid id is discarded`() {
        assertNull(parseSplitPush(mapOf("type" to "offers", "monthId" to "AbC123")))
        assertNull(parseSplitPush(mapOf("monthId" to "AbC123")))
        assertNull(parseSplitPush(mapOf("type" to "sncount_closed")))
        assertNull(parseSplitPush(mapOf("type" to "sncount_closed", "monthId" to "")))
        assertNull(parseSplitPush(mapOf("type" to "sncount_closed", "monthId" to "a/b")))
        assertNull(parseSplitPush(mapOf("type" to "sncount_closed", "monthId" to "a#b")))
        assertNull(parseSplitPush(emptyMap()))
    }

    @Test fun `types match the NAS notifier`() {
        // Tienen que coincidir con PUSH_CLOSED y PUSH_REMINDER de tools/notifier/src/logic.mjs.
        assertEquals("sncount_closed", PUSH_TYPE_SPLIT_CLOSED)
        assertEquals("sncount_reminder", PUSH_TYPE_SPLIT_REMINDER)
    }

    @Test fun `closed and reminder share the notification id of the month`() {
        assertEquals(splitPushNotificationId("AbC123"), splitPushNotificationId("AbC123"))
    }
}

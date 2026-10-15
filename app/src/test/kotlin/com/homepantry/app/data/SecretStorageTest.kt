package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretStorageTest {
    private class FakeStorage(var value: String? = null) : SecretStorage {
        var cleared = false
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun clear() { value = null; cleared = true }
    }

    @Test fun `copies the legacy value when the new store is empty and clears the legacy one`() {
        val legacy = FakeStorage("clave-antigua")
        val target = FakeStorage()
        assertEquals("clave-antigua", migrateSecret(legacy, target))
        assertEquals("clave-antigua", target.value)
        assertNull(legacy.value)
    }

    @Test fun `keeps the new value when both stores have one, and still clears the legacy one`() {
        val legacy = FakeStorage("clave-antigua")
        val target = FakeStorage("clave-nueva")
        assertEquals("clave-nueva", migrateSecret(legacy, target))
        assertEquals("clave-nueva", target.value)
        assertNull(legacy.value)
    }

    @Test fun `with nothing in the legacy store returns the new value and touches nothing`() {
        val legacy = FakeStorage()
        val target = FakeStorage("clave-nueva")
        assertEquals("clave-nueva", migrateSecret(legacy, target))
        assertEquals(false, legacy.cleared)
    }

    @Test fun `with both stores empty returns null`() {
        assertNull(migrateSecret(FakeStorage(), FakeStorage()))
    }

    private class UnreadableStorage : SecretStorage {
        override fun read(): String? = throw IllegalStateException("no se pudo abrir")
        override fun write(value: String) = throw UnsupportedOperationException()
        override fun clear() = throw UnsupportedOperationException()
    }

    @Test fun `migrating all pairs reports success when every legacy store could be read`() {
        val target = FakeStorage()
        assertEquals(true, migrateAllSecrets(listOf(FakeStorage("clave") to target)))
        assertEquals("clave", target.value)
    }

    @Test fun `an unreadable legacy store reports failure and leaves the target untouched`() {
        val target = FakeStorage()
        assertEquals(false, migrateAllSecrets(listOf(UnreadableStorage() to target)))
        assertNull(target.value)
    }
}

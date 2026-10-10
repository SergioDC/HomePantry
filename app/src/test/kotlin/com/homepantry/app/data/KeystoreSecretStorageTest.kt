package com.homepantry.app.data

import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Test

class KeystoreSecretStorageTest {
    @Test fun `corrupt or undecryptable values are permanent failures`() {
        assertEquals(true, isPermanentDecryptFailure(AEADBadTagException("tag")))
        assertEquals(true, isPermanentDecryptFailure(IllegalArgumentException("bad base64")))
    }

    @Test fun `transient keystore errors are not permanent, so the stored value is kept`() {
        assertEquals(false, isPermanentDecryptFailure(KeyStoreException("busy")))
        assertEquals(false, isPermanentDecryptFailure(ProviderException("keystore daemon")))
        assertEquals(false, isPermanentDecryptFailure(IllegalStateException("not ready")))
    }
}

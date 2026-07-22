package org.cyphr.app.crypto

import org.junit.Assert.*
import org.junit.Test

class SenderResolutionTest {

    @Test
    fun knownContactStoresUuidAndName() {
        val r = SenderResolution.KnownContact("uuid-1", "Alice", "verified", false)
        assertEquals("uuid-1", r.contactUuid)
        assertEquals("Alice", r.displayName)
        assertEquals("verified", r.status)
        assertFalse(r.keyChanged)
    }

    @Test
    fun knownContactSurfacesUnverifiedAndKeyChanged() {
        val r = SenderResolution.KnownContact("uuid-2", "Bob", "unverified", true)
        assertEquals("unverified", r.status)
        assertTrue(r.keyChanged)
    }

    @Test
    fun unknownKeyStoresShortFingerprint() {
        val fp = "ABCD 1234"
        val r = SenderResolution.UnknownKey(fp)
        assertEquals(fp, r.shortFingerprint)
    }

    @Test
    fun noSenderInfoIsSingleton() {
        assertSame(SenderResolution.NoSenderInfo, SenderResolution.NoSenderInfo)
    }

    @Test
    fun whenExhaustiveCoversAllThreeBranches() {
        val results: List<SenderResolution> = listOf(
            SenderResolution.KnownContact("u", "n", "unverified", false),
            SenderResolution.UnknownKey("fp"),
            SenderResolution.NoSenderInfo
        )
        val labels = results.map { r ->
            when (r) {
                is SenderResolution.KnownContact -> "known"
                is SenderResolution.UnknownKey -> "unknown"
                SenderResolution.NoSenderInfo -> "none"
            }
        }
        assertEquals(listOf("known", "unknown", "none"), labels)
    }
}

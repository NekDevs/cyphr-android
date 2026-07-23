package org.cyphr.app.crypto

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExchangeBlobTest {

    private val testKey = ByteArray(32) { it.toByte() }

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun buildProducesNonEmptyString() {
        val blob = ExchangeBlob.build(testKey, 1)
        assertNotNull(blob)
        assertTrue(blob.isNotEmpty())
    }

    @Test
    fun buildAndParseRoundTrip() {
        val epoch = 42
        val blob = ExchangeBlob.build(testKey, epoch)
        val parsed = ExchangeBlob.parse(blob)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertEquals(ExchangeBlob.ALGORITHM_TAG, parsed.algorithmTag)
        assertEquals(epoch, parsed.keyEpoch)
        assertArrayEquals(testKey, parsed.publicKeyBytes)
    }

    @Test
    fun buildAndParseWithEpochZero() {
        val blob = ExchangeBlob.build(testKey, 0)
        val parsed = ExchangeBlob.parse(blob)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.keyEpoch)
    }

    @Test
    fun buildAndParseWithEpochMax() {
        val blob = ExchangeBlob.build(testKey, 65535)
        val parsed = ExchangeBlob.parse(blob)
        assertNotNull(parsed)
        assertEquals(65535, parsed!!.keyEpoch)
    }

    @Test
    fun parseReturnsNullForGarbage() {
        assertNull(ExchangeBlob.parse("!!!not-a-blob!!!"))
    }

    @Test
    fun parseReturnsNullForEmptyString() {
        assertNull(ExchangeBlob.parse(""))
    }

    @Test
    fun parseReturnsNullForInvalidVersion() {
        val raw = byteArrayOf(0xFF.toByte(), 0x01, 0x41)
        val encoded = android.util.Base64.encodeToString(raw, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        assertNull(ExchangeBlob.parse(encoded))
    }

    @Test
    fun fingerprintIs64CharHex() {
        val fp = ExchangeBlob.fingerprint(testKey)
        assertEquals(64, fp.length)
        fp.forEach { assertTrue(it in '0'..'9' || it in 'A'..'F') }
    }

    @Test
    fun fingerprintIsDeterministic() {
        assertEquals(ExchangeBlob.fingerprint(testKey), ExchangeBlob.fingerprint(testKey))
    }

    @Test
    fun differentKeysHaveDifferentFingerprints() {
        val keyB = ByteArray(32) { (it + 1).toByte() }
        assertNotEquals(ExchangeBlob.fingerprint(testKey), ExchangeBlob.fingerprint(keyB))
    }

    @Test
    fun shortFingerprintIs9CharsWithSpace() {
        val sfp = ExchangeBlob.shortFingerprint(testKey)
        assertEquals(9, sfp.length)
        assertEquals(' ', sfp[4])
    }

    @Test
    fun shortFingerprintMatchesFirst8HexChars() {
        val fp = ExchangeBlob.fingerprint(testKey)
        val sfp = ExchangeBlob.shortFingerprint(testKey)
        val expected = "${fp.substring(0, 4)} ${fp.substring(4, 8)}"
        assertEquals(expected, sfp)
    }

    @Test
    fun parseRejectsTruncatedBlob() {
        val full = ExchangeBlob.build(testKey, 1)
        val truncated = full.substring(0, full.length / 2)
        assertNull(ExchangeBlob.parse(truncated))
    }
}

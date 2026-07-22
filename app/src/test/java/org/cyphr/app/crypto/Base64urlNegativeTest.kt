package org.cyphr.app.crypto

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Base64urlNegativeTest {

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun encodeReturnsNullWhenFlagIsDisabled() {
        CryptoFeatureFlag.disable()
        assertNull(Base64UrlCodec.encode("test".toByteArray()))
    }

    @Test
    fun decodeReturnsNullWhenFlagIsDisabled() {
        CryptoFeatureFlag.disable()
        assertNull(Base64UrlCodec.decode("dGVzdA"))
    }

    @Test
    fun encodeReturnsNullWhenFlagIsDisabledThenEnabled() {
        CryptoFeatureFlag.disable()
        assertNull(Base64UrlCodec.encode("data".toByteArray()))
        CryptoFeatureFlag.enable()
    }
}

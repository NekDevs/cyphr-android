package org.cyphr.app.crypto

import org.junit.Assert.*
import org.junit.Test

class CryptoFeatureFlagTest {

    @Test
    fun isEnabledByDefault() {
        assertTrue(CryptoFeatureFlag.isEnabled)
    }

    @Test
    fun disableSetsIsEnabledToFalse() {
        CryptoFeatureFlag.disable()
        assertFalse(CryptoFeatureFlag.isEnabled)
        CryptoFeatureFlag.enable()
    }

    @Test
    fun enableRestoresIsEnabled() {
        CryptoFeatureFlag.disable()
        assertFalse(CryptoFeatureFlag.isEnabled)
        CryptoFeatureFlag.enable()
        assertTrue(CryptoFeatureFlag.isEnabled)
    }

    @Test
    fun doubleDisableKeepsDisabled() {
        CryptoFeatureFlag.disable()
        CryptoFeatureFlag.disable()
        assertFalse(CryptoFeatureFlag.isEnabled)
        CryptoFeatureFlag.enable()
    }

    @Test
    fun doubleEnableKeepsEnabled() {
        CryptoFeatureFlag.enable()
        CryptoFeatureFlag.enable()
        assertTrue(CryptoFeatureFlag.isEnabled)
    }

    @Test
    fun enableAfterDisableWorks() {
        CryptoFeatureFlag.disable()
        CryptoFeatureFlag.enable()
        assertTrue(CryptoFeatureFlag.isEnabled)
    }
}

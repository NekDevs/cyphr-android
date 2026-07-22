package org.cyphr.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun biometricUnlockDefaultsToDisabled() {
        assertFalse(AppSettings.isBiometricUnlockEnabled(context))
    }

    @Test
    fun biometricUnlockRoundTrip() {
        AppSettings.setBiometricUnlockEnabled(context, true)
        assertTrue(AppSettings.isBiometricUnlockEnabled(context))
        AppSettings.setBiometricUnlockEnabled(context, false)
        assertFalse(AppSettings.isBiometricUnlockEnabled(context))
    }

    @Test
    fun onboardingDefaultsToNotCompleted() {
        assertFalse(AppSettings.isOnboardingCompleted(context))
    }

    @Test
    fun onboardingRoundTrip() {
        AppSettings.setOnboardingCompleted(context, true)
        assertTrue(AppSettings.isOnboardingCompleted(context))
        AppSettings.setOnboardingCompleted(context, false)
        assertFalse(AppSettings.isOnboardingCompleted(context))
    }

    @Test
    fun selectedContactUuidDefaultsToNull() {
        assertNull(AppSettings.getSelectedContactUuid(context))
    }

    @Test
    fun selectedContactUuidRoundTrip() {
        AppSettings.setSelectedContactUuid(context, "test-uuid", "Test Name")
        assertEquals("test-uuid", AppSettings.getSelectedContactUuid(context))
        assertEquals("Test Name", AppSettings.getSelectedContactName(context))
    }

    @Test
    fun clearSelectedContact() {
        AppSettings.setSelectedContactUuid(context, "uuid-1", "Name 1")
        AppSettings.setSelectedContactUuid(context, null, null)
        assertNull(AppSettings.getSelectedContactUuid(context))
        assertNull(AppSettings.getSelectedContactName(context))
    }

    @Test
    fun encryptionTileDefaultsToInactive() {
        assertFalse(AppSettings.isEncryptionTileActive(context))
    }

    @Test
    fun encryptionTileRoundTrip() {
        AppSettings.setEncryptionTileActive(context, true)
        assertTrue(AppSettings.isEncryptionTileActive(context))
        AppSettings.setEncryptionTileActive(context, false)
        assertFalse(AppSettings.isEncryptionTileActive(context))
    }

    @Test
    fun encryptModeDefaultsToEnabled() {
        assertTrue(AppSettings.isEncryptModeEnabled(context))
    }

    @Test
    fun encryptModeRoundTrip() {
        AppSettings.setEncryptModeEnabled(context, false)
        assertFalse(AppSettings.isEncryptModeEnabled(context))
        AppSettings.setEncryptModeEnabled(context, true)
        assertTrue(AppSettings.isEncryptModeEnabled(context))
    }

    @Test
    fun keyboardLanguageCodeDefaultsToNull() {
        assertNull(AppSettings.getKeyboardLanguageCode(context))
    }

    @Test
    fun keyboardLanguageCodeRoundTrip() {
        AppSettings.setKeyboardLanguageCode(context, "en")
        assertEquals("en", AppSettings.getKeyboardLanguageCode(context))
        AppSettings.setKeyboardLanguageCode(context, null)
        assertNull(AppSettings.getKeyboardLanguageCode(context))
    }

    @Test
    fun allSettingsPersistTogether() {
        AppSettings.setBiometricUnlockEnabled(context, true)
        AppSettings.setOnboardingCompleted(context, true)
        AppSettings.setSelectedContactUuid(context, "multi-uuid", "Multi Name")
        AppSettings.setEncryptionTileActive(context, true)
        AppSettings.setEncryptModeEnabled(context, false)
        AppSettings.setKeyboardLanguageCode(context, "de")

        assertTrue(AppSettings.isBiometricUnlockEnabled(context))
        assertTrue(AppSettings.isOnboardingCompleted(context))
        assertEquals("multi-uuid", AppSettings.getSelectedContactUuid(context))
        assertEquals("Multi Name", AppSettings.getSelectedContactName(context))
        assertTrue(AppSettings.isEncryptionTileActive(context))
        assertFalse(AppSettings.isEncryptModeEnabled(context))
        assertEquals("de", AppSettings.getKeyboardLanguageCode(context))
    }
}

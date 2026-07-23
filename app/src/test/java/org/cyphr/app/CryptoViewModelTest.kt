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
class CryptoViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: CryptoViewModel

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        context = ApplicationProvider.getApplicationContext()
        viewModel = CryptoViewModel()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun initializeLoadsDefaultProfile() {
        viewModel.initialize(context)
        assertEquals(CryptoState.DEFAULT_PROFILE_UUID, viewModel.activeProfileUuid)
        assertNotNull(viewModel.profileKeysetBytes)
        assertNotNull(viewModel.profilePublicKeyBytes)
        assertNull(viewModel.initializationError)
    }

    @Test
    fun initializeDoesNotChangeStateIfAlreadyInitialized() {
        viewModel.initialize(context)
        val firstKeyset = viewModel.profileKeysetBytes
        viewModel.initialize(context)
        assertArrayEquals(firstKeyset, viewModel.profileKeysetBytes)
    }

    @Test
    fun listProfilesReturnsDefaultAfterInitialize() {
        viewModel.initialize(context)
        val profiles = viewModel.listProfiles()
        assertTrue(profiles.isNotEmpty())
        assertTrue(profiles.any { it.uuid == CryptoState.DEFAULT_PROFILE_UUID })
    }

    @Test
    fun createProfileReturnsNewProfileInfo() {
        viewModel.initialize(context)
        val created = viewModel.createProfile(context, "test-create-uuid", "Test Profile")
        assertNotNull(created)
        assertEquals("test-create-uuid", created!!.uuid)
        assertEquals("Test Profile", created.displayName)
        assertEquals("test-create-uuid", viewModel.activeProfileUuid)
    }

    @Test
    fun switchProfileChangesActiveProfile() {
        viewModel.initialize(context)
        viewModel.createProfile(context, "switch-target", "Target")
        val switched = viewModel.switchProfile(context, CryptoState.DEFAULT_PROFILE_UUID)
        assertTrue(switched)
        assertEquals(CryptoState.DEFAULT_PROFILE_UUID, viewModel.activeProfileUuid)
    }

    @Test
    fun switchProfileReturnsFalseForNonexistentProfile() {
        viewModel.initialize(context)
        assertFalse(viewModel.switchProfile(context, "nonexistent-uuid"))
    }

    @Test
    fun getMyFingerprintReturnsNonNullAfterInitialize() {
        viewModel.initialize(context)
        assertNotNull(viewModel.getMyFingerprint())
    }

    @Test
    fun getMyShortFingerprintReturnsNonNullAfterInitialize() {
        viewModel.initialize(context)
        assertNotNull(viewModel.getMyShortFingerprint())
    }

    @Test
    fun getMyExchangeBlobReturnsNonNullAfterInitialize() {
        viewModel.initialize(context)
        val blob = viewModel.getMyExchangeBlob()
        assertNotNull(blob)
        assertTrue(blob!!.isNotEmpty())
    }

    @Test
    fun rotateKeysProducesNewKeyset() {
        viewModel.initialize(context)
        val original = viewModel.profileKeysetBytes
        val rotated = viewModel.rotateKeys(context)
        assertTrue(rotated)
        assertNotNull(viewModel.profileKeysetBytes)
        assertNotSame(original, viewModel.profileKeysetBytes)
    }

    @Test
    fun renameProfileUpdatesDisplayName() {
        viewModel.initialize(context)
        val renamed = viewModel.renameProfile(context, CryptoState.DEFAULT_PROFILE_UUID, "New Name")
        assertTrue(renamed)
        val profiles = viewModel.listProfiles()
        val updated = profiles.find { it.uuid == CryptoState.DEFAULT_PROFILE_UUID }
        assertNotNull(updated)
        assertEquals("New Name", updated!!.displayName)
    }
}

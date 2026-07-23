package org.cyphr.app

import android.content.Context
import androidx.lifecycle.ViewModel
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.EncryptedStore
import org.cyphr.app.crypto.EncryptedStoreException
import org.cyphr.app.crypto.ExchangeBlob
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ProfileKeyManager.ProfileInfo
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CryptoViewModel : ViewModel() {

    var activeProfileUuid: String = CryptoState.DEFAULT_PROFILE_UUID
    var profileKeysetBytes: ByteArray? = null
    var profilePublicKeyBytes: ByteArray? = null
    var initializationError: String? = null
        private set

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!CryptoFeatureFlag.isEnabled) return
        if (profileKeysetBytes != null) return

        try {
            val persisted = loadActiveProfileUuid(context)
            val target = if (persisted != null && profileExists(context, persisted)) persisted else CryptoState.DEFAULT_PROFILE_UUID
            loadProfileIntoMemory(context, target)
            initializationError = null
        } catch (e: EncryptedStoreException) {
            initializationError = e.message
        }
    }

    fun clearInitializationError() {
        initializationError = null
    }

    fun listProfiles(): List<ProfileInfo> {
        val ctx = appContext ?: return emptyList()
        return ProfileKeyManager.listProfiles(ctx)
    }

    fun switchProfile(context: Context, uuid: String): Boolean {
        if (uuid == activeProfileUuid) return true
        val keys = ProfileKeyManager.loadProfileKeys(context, uuid) ?: return false
        val prevUuid = activeProfileUuid
        val prevKeyset = profileKeysetBytes
        val prevPubKey = profilePublicKeyBytes
        return try {
            activeProfileUuid = uuid
            profileKeysetBytes = keys
            profilePublicKeyBytes = ProfileKeyManager.getPublicKey(keys)
            persistActiveProfileUuid(context, uuid)
            true
        } catch (e: EncryptedStoreException) {
            activeProfileUuid = prevUuid
            profileKeysetBytes = prevKeyset
            profilePublicKeyBytes = prevPubKey
            false
        }
    }

    fun createProfile(context: Context, uuid: String = UUID.randomUUID().toString(), displayName: String? = null): ProfileInfo? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            val keys = ProfileKeyManager.generateProfileKeys(context) ?: return null
            ProfileKeyManager.saveProfileKeys(context, uuid, keys)
            ProfileKeyManager.saveProfileMetadata(context, uuid, displayName)
            val meta = ProfileKeyManager.loadProfileMetadata(context, uuid) ?: return null
            activeProfileUuid = uuid
            profileKeysetBytes = keys
            profilePublicKeyBytes = ProfileKeyManager.getPublicKey(keys)
            persistActiveProfileUuid(context, uuid)
            ProfileInfo(
                uuid = uuid,
                displayName = meta.optString("displayName", uuid),
                algorithmTag = meta.optString("algorithmTag", ""),
                createdAt = meta.optString("createdAt", ""),
                status = meta.optString("status", "active"),
                keyEpoch = meta.optInt("keyEpoch", 1)
            )
        } catch (e: EncryptedStoreException) {
            null
        }
    }

    private fun loadProfileIntoMemory(context: Context, uuid: String) {
        val existing = ProfileKeyManager.loadProfileKeys(context, uuid)
        if (existing != null) {
            activeProfileUuid = uuid
            profileKeysetBytes = existing
            profilePublicKeyBytes = ProfileKeyManager.getPublicKey(existing)
        } else {
            val generated = ProfileKeyManager.generateProfileKeys(context) ?: return
            ProfileKeyManager.saveProfileKeys(context, uuid, generated)
            ProfileKeyManager.saveProfileMetadata(context, uuid)
            activeProfileUuid = uuid
            profileKeysetBytes = generated
            profilePublicKeyBytes = ProfileKeyManager.getPublicKey(generated)
        }
        try {
            persistActiveProfileUuid(context, activeProfileUuid)
        } catch (e: EncryptedStoreException) {
            initializationError = e.message
        }
    }

    private fun activeProfileFile(context: Context): File =
        File(context.filesDir, "cyphr/active-profile.json")

    private fun persistActiveProfileUuid(context: Context, uuid: String) {
        val json = JSONObject().apply { put("activeProfileUuid", uuid) }
        EncryptedStore.writeText(context, activeProfileFile(context), json.toString())
    }

    private fun loadActiveProfileUuid(context: Context): String? {
        val text = EncryptedStore.readText(context, activeProfileFile(context))
        if (text == null) return null
        return try {
            val json = JSONObject(text)
            json.optString("activeProfileUuid", CryptoState.DEFAULT_PROFILE_UUID)
        } catch (_: Exception) {
            null
        }
    }

    private fun profileExists(context: Context, uuid: String): Boolean =
        ProfileKeyManager.loadProfileKeys(context, uuid) != null

    fun getMyFingerprint(): String? =
        profilePublicKeyBytes?.let { ExchangeBlob.fingerprint(it) }

    fun getMyShortFingerprint(): String? =
        profilePublicKeyBytes?.let { ExchangeBlob.shortFingerprint(it) }

    fun rotateKeys(context: Context): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        return try {
            val newKeyset = ProfileKeyManager.rotateProfileKeys(context, activeProfileUuid) ?: return false
            profileKeysetBytes = newKeyset
            profilePublicKeyBytes = ProfileKeyManager.getPublicKey(newKeyset)
            true
        } catch (e: EncryptedStoreException) {
            false
        }
    }

    fun renameProfile(context: Context, uuid: String, newName: String): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        return try {
            ProfileKeyManager.updateProfileMetadata(context, uuid, newName)
            true
        } catch (e: EncryptedStoreException) {
            false
        }
    }

    fun deleteProfile(context: Context, uuid: String): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        if (ProfileKeyManager.listProfiles(context).size <= 1) return false
        ProfileKeyManager.deleteProfile(context, uuid)
        if (uuid == activeProfileUuid) {
            val remaining = ProfileKeyManager.listProfiles(context)
            if (remaining.isNotEmpty()) {
                switchProfile(context, remaining.first().uuid)
            } else {
                activeProfileUuid = CryptoState.DEFAULT_PROFILE_UUID
                profileKeysetBytes = null
                profilePublicKeyBytes = null
                try {
                    persistActiveProfileUuid(context, CryptoState.DEFAULT_PROFILE_UUID)
                } catch (e: EncryptedStoreException) {
                    initializationError = e.message
                }
            }
        }
        return true
    }

    fun getMyExchangeBlob(): String? {
        val pubKey = profilePublicKeyBytes ?: return null
        val ctx = appContext ?: return null
        val meta = ProfileKeyManager.loadProfileMetadata(ctx, activeProfileUuid)
        val epoch = meta?.optInt("keyEpoch", 1) ?: 1
        return ExchangeBlob.build(pubKey, epoch)
    }
}

package org.cyphr.app.crypto

import android.content.Context
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HybridConfig
import org.json.JSONObject
import java.io.File

object ProfileKeyManager {

    private const val ALGORITHM_TAG = "HPKE-X25519-AES256GCM"

    private val hpkeParams: HpkeParameters by lazy {
        HpkeParameters.builder()
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .build()
    }

    fun generateProfileKeys(context: Context): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            HybridConfig.register()
            val handle = KeysetHandle.generateNew(hpkeParams)
            TinkProtoKeysetFormat.serializeKeyset(
                handle,
                InsecureSecretKeyAccess.get(),
                RegistryConfiguration.get()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveProfileKeys(context: Context, profileUuid: String, keysetBytes: ByteArray) {
        if (!CryptoFeatureFlag.isEnabled) return
        EncryptedStore.writeBytes(context, File(profileDir(context, profileUuid), "keyset.bin"), keysetBytes)
    }

    fun loadProfileKeys(context: Context, profileUuid: String): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return EncryptedStore.readBytes(context, File(profileDir(context, profileUuid), "keyset.bin"))
    }

    fun getPublicKey(keysetBytes: ByteArray): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            HybridConfig.register()
            val handle = TinkProtoKeysetFormat.parseKeyset(
                keysetBytes,
                InsecureSecretKeyAccess.get(),
                RegistryConfiguration.get()
            )
            val primaryKey = handle.getPrimary().getKey()
            val privateKey = primaryKey as? HpkePrivateKey ?: return null
            privateKey.publicKey.publicKeyBytes.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    fun saveProfileMetadata(context: Context, profileUuid: String, displayName: String? = null, keyEpoch: Int = 1) {
        if (!CryptoFeatureFlag.isEnabled) return
        val dir = profileDir(context, profileUuid)
        dir.mkdirs()
        val json = JSONObject().apply {
            put("profileUuid", profileUuid)
            put("displayName", displayName ?: defaultDisplayName(profileUuid))
            put("algorithmTag", ALGORITHM_TAG)
            put("createdAt", ISO_8601_DATE_FORMAT.format(java.util.Date()))
            put("status", "active")
            put("keyEpoch", keyEpoch)
        }
        writeMetadataFile(context, profileUuid, json)
    }

    fun loadProfileMetadata(context: Context, profileUuid: String): JSONObject? {
        if (!CryptoFeatureFlag.isEnabled) return null
        val text = EncryptedStore.readText(context, File(profileDir(context, profileUuid), "keyset-info.json"))
        return if (text != null) try { JSONObject(text) } catch (_: Exception) { null } else null
    }

    data class ProfileInfo(
        val uuid: String,
        val displayName: String,
        val algorithmTag: String,
        val createdAt: String,
        val status: String,
        val keyEpoch: Int
    )

    fun listProfiles(context: Context): List<ProfileInfo> {
        if (!CryptoFeatureFlag.isEnabled) return emptyList()
        val profilesDir = File(context.filesDir, "cyphr/profiles")
        if (!profilesDir.isDirectory) return emptyList()
        return profilesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val meta = loadProfileMetadata(context, dir.name) ?: return@mapNotNull null
                ProfileInfo(
                    uuid = dir.name,
                    displayName = meta.optString("displayName", defaultDisplayName(dir.name)),
                    algorithmTag = meta.optString("algorithmTag", ALGORITHM_TAG),
                    createdAt = meta.optString("createdAt", ""),
                    status = meta.optString("status", "unknown"),
                    keyEpoch = meta.optInt("keyEpoch", 1)
                )
            } ?: emptyList()
    }

    fun rotateProfileKeys(context: Context, profileUuid: String): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            val currentKeyset = loadProfileKeys(context, profileUuid)
            if (currentKeyset != null) {
                savePreviousProfileKeys(context, profileUuid, currentKeyset)
            }
            val newKeyset = generateProfileKeys(context) ?: return null
            saveProfileKeys(context, profileUuid, newKeyset)
            val meta = loadProfileMetadata(context, profileUuid) ?: return null
            val currentEpoch = meta.optInt("keyEpoch", 1)
            meta.put("keyEpoch", currentEpoch + 1)
            meta.put("rotatedAt", ISO_8601_DATE_FORMAT.format(java.util.Date()))
            writeMetadataFile(context, profileUuid, meta)
            newKeyset
        } catch (_: Exception) {
            null
        }
    }

    fun loadPreviousProfileKeys(context: Context, profileUuid: String): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return EncryptedStore.readBytes(context, File(profileDir(context, profileUuid), "previous-keyset.bin"))
    }

    private fun savePreviousProfileKeys(context: Context, profileUuid: String, keysetBytes: ByteArray) {
        EncryptedStore.writeBytes(context, File(profileDir(context, profileUuid), "previous-keyset.bin"), keysetBytes)
    }

    private fun writeMetadataFile(context: Context, profileUuid: String, json: JSONObject) {
        EncryptedStore.writeText(context, File(profileDir(context, profileUuid), "keyset-info.json"), json.toString())
    }

    fun isKeyRecentlyRotated(context: Context, profileUuid: String): Boolean {
        val meta = loadProfileMetadata(context, profileUuid) ?: return false
        val rotatedAt = meta.optString("rotatedAt", "")
        if (rotatedAt.isEmpty()) return false
        return try {
            val time = ISO_8601_DATE_FORMAT.parse(rotatedAt) ?: return false
            System.currentTimeMillis() - time.time < 3600_000L
        } catch (_: Exception) {
            false
        }
    }

    fun loadActiveProfileUuid(context: Context): String? {
        val file = File(context.filesDir, "cyphr/active-profile.json")
        val text = EncryptedStore.readText(context, file)
        if (text == null) return null
        return try {
            JSONObject(text).optString("activeProfileUuid", "default")
        } catch (_: Exception) {
            null
        }
    }

    fun listAllProfileUuids(context: Context): List<String> {
        val profilesDir = File(context.filesDir, "cyphr/profiles")
        if (!profilesDir.isDirectory) return emptyList()
        return profilesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun updateProfileMetadata(context: Context, profileUuid: String, displayName: String) {
        if (!CryptoFeatureFlag.isEnabled) return
        val meta = loadProfileMetadata(context, profileUuid) ?: return
        meta.put("displayName", displayName)
        writeMetadataFile(context, profileUuid, meta)
    }

    fun deleteProfile(context: Context, profileUuid: String): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        val dir = profileDir(context, profileUuid)
        return if (dir.exists()) {
            dir.deleteRecursively()
        } else false
    }

    fun profileDir(context: Context, profileUuid: String): File =
        File(context.filesDir, "cyphr/profiles/$profileUuid")

    private fun defaultDisplayName(uuid: String): String =
        if (uuid == "default") "Personal" else "Profile ${uuid.take(8)}"

    private val ISO_8601_DATE_FORMAT: java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
}

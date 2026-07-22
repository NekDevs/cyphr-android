package org.cyphr.app.crypto

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class SenderResolution {
    data class KnownContact(
        val contactUuid: String,
        val displayName: String,
        val status: String,
        val keyChanged: Boolean
    ) : SenderResolution()
    data class UnknownKey(val shortFingerprint: String) : SenderResolution()
    data object NoSenderInfo : SenderResolution()
}

data class ContactKeyMeta(
    val contactUuid: String,
    val displayName: String,
    val keyEpoch: Int,
    val status: String,
    val shortFingerprint: String,
    val keyChanged: Boolean = false
)

object ContactKeyStore {

    /**
     * Returns true if the on-disk public key's fingerprint matches the
     * fingerprint stored in the contact metadata. A mismatch indicates
     * the key has been replaced since the contact was originally saved.
     */
    fun keyFingerprintMatchesMetadata(
        context: Context,
        profileUuid: String,
        contactUuid: String
    ): Boolean {
        val meta = loadContactMetadata(context, profileUuid, contactUuid) ?: return true
        val storedFp = meta.optString("publicKeyFingerprint", "")
        if (storedFp.isEmpty()) return true
        val rawKey = loadContactPublicKey(context, profileUuid, contactUuid) ?: return true
        val computedFp = ExchangeBlob.fingerprint(rawKey)
        return computedFp == storedFp
    }

    /**
     * Searches all contacts in the given profile and returns the UUID of
     * the first contact whose stored fingerprint matches [fingerprint],
     * or null if no match is found.
     */
    fun findContactByFingerprint(
        context: Context,
        profileUuid: String,
        fingerprint: String
    ): String? {
        val contactsDir = File(profileDir(context, profileUuid), "contacts")
        if (!contactsDir.isDirectory) return null
        return contactsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.firstOrNull { dir ->
                val meta = loadContactMetadata(context, profileUuid, dir.name) ?: return@firstOrNull false
                meta.optString("publicKeyFingerprint", "") == fingerprint
            }
            ?.name
    }

    /**
     * Resolves a parsed sender public key (from a v2 payload) against
     * saved contacts for the given [profileUuid].
     *
     * Returns:
     * - [SenderResolution.KnownContact] if the sender key's fingerprint
     *   matches a stored contact.
     * - [SenderResolution.UnknownKey] if the sender key is present but
     *   does not match any saved contact.
     * - [SenderResolution.NoSenderInfo] if [senderPublicKeyBytes] is
     *   null (i.e. a v1 payload with no sender identity).
     */
    fun resolveSender(
        context: Context,
        profileUuid: String,
        senderPublicKeyBytes: ByteArray?
    ): SenderResolution {
        if (senderPublicKeyBytes == null) return SenderResolution.NoSenderInfo
        val fp = ExchangeBlob.fingerprint(senderPublicKeyBytes)
        val contactUuid = findContactByFingerprint(context, profileUuid, fp)
        if (contactUuid != null) {
            val meta = getContact(context, profileUuid, contactUuid)
            return SenderResolution.KnownContact(
                contactUuid = contactUuid,
                displayName = meta?.displayName ?: contactUuid,
                status = meta?.status ?: "unverified",
                keyChanged = meta?.keyChanged ?: false
            )
        }
        return SenderResolution.UnknownKey(
            shortFingerprint = ExchangeBlob.shortFingerprint(senderPublicKeyBytes)
        )
    }

    private val ISO_8601: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun updateContactFromBlob(
        context: Context,
        profileUuid: String,
        contactUuid: String,
        publicKeyBytes: ByteArray,
        algorithmTag: String,
        keyEpoch: Int
    ): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        val meta = loadContactMetadata(context, profileUuid, contactUuid) ?: return false
        saveContactPublicKey(context, profileUuid, contactUuid, publicKeyBytes, keyEpoch)
        val fp = ExchangeBlob.fingerprint(publicKeyBytes)
        meta.put("publicKeyAlgorithm", algorithmTag)
        meta.put("keyEpoch", keyEpoch)
        meta.put("publicKeyFingerprint", fp)
        meta.put("verificationStatus", "unverified")
        saveContactMetadata(context, profileUuid, contactUuid, meta)
        return true
    }

    fun saveContactWithFullMetadata(
        context: Context,
        profileUuid: String,
        contactUuid: String,
        displayName: String,
        publicKeyBytes: ByteArray,
        algorithmTag: String,
        keyEpoch: Int,
        fingerprint: String,
        verificationStatus: String
    ) {
        saveContactPublicKey(context, profileUuid, contactUuid, publicKeyBytes, keyEpoch)
        val now = ISO_8601.format(Date())
        val json = JSONObject().apply {
            put("contactUuid", contactUuid)
            put("displayName", displayName)
            put("publicKeyAlgorithm", algorithmTag)
            put("keyEpoch", keyEpoch)
            put("publicKeyFingerprint", fingerprint)
            put("verificationStatus", verificationStatus)
            put("sendCounter", 0)
            put("receiveCounter", 0)
            put("createdAt", now)
        }
        saveContactMetadata(context, profileUuid, contactUuid, json)
    }

    fun updateContactVerificationStatus(
        context: Context,
        profileUuid: String,
        contactUuid: String,
        status: String
    ) {
        val meta = loadContactMetadata(context, profileUuid, contactUuid) ?: return
        meta.put("verificationStatus", status)
        if (status == "verified") {
            meta.put("verifiedAt", ISO_8601.format(Date()))
        }
        saveContactMetadata(context, profileUuid, contactUuid, meta)
    }

    fun saveContactPublicKey(
        context: Context,
        profileUuid: String,
        contactUuid: String,
        publicKeyBytes: ByteArray,
        epoch: Int
    ) {
        if (!CryptoFeatureFlag.isEnabled) return
        EncryptedStore.writeBytes(context, File(contactDir(context, profileUuid, contactUuid), "public-key.bin"), publicKeyBytes)
    }

    fun loadContactPublicKey(
        context: Context,
        profileUuid: String,
        contactUuid: String
    ): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return EncryptedStore.readBytes(context, File(contactDir(context, profileUuid, contactUuid), "public-key.bin"))
    }

    fun saveContactMetadata(
        context: Context,
        profileUuid: String,
        contactUuid: String,
        metadata: JSONObject
    ) {
        if (!CryptoFeatureFlag.isEnabled) return
        EncryptedStore.writeText(context, File(contactDir(context, profileUuid, contactUuid), "contact-info.json"), metadata.toString())
    }

    fun loadContactMetadata(
        context: Context,
        profileUuid: String,
        contactUuid: String
    ): JSONObject? {
        if (!CryptoFeatureFlag.isEnabled) return null
        val text = EncryptedStore.readText(context, File(contactDir(context, profileUuid, contactUuid), "contact-info.json"))
        return if (text != null) try { JSONObject(text) } catch (_: Exception) { null } else null
    }

    fun listContactKeys(context: Context, profileUuid: String): List<ContactKeyMeta> {
        if (!CryptoFeatureFlag.isEnabled) return emptyList()
        val contactsDir = File(profileDir(context, profileUuid), "contacts")
        if (!contactsDir.isDirectory) return emptyList()
        return contactsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val meta = loadContactMetadata(context, profileUuid, dir.name) ?: return@mapNotNull null
                val fp = meta.optString("publicKeyFingerprint", "")
                val keyChanged = if (fp.isNotEmpty()) {
                    val rawKey = EncryptedStore.readBytes(context, File(dir, "public-key.bin"))
                    rawKey != null && ExchangeBlob.fingerprint(rawKey) != fp
                } else false
                ContactKeyMeta(
                    contactUuid = dir.name,
                    displayName = meta.optString("displayName", dir.name),
                    keyEpoch = meta.optInt("keyEpoch", 1),
                    status = meta.optString("verificationStatus", "unverified"),
                    shortFingerprint = if (fp.length >= 8) "${fp.substring(0, 4)} ${fp.substring(4, 8)}" else fp,
                    keyChanged = keyChanged
                )
            } ?: emptyList()
    }

    fun getContact(context: Context, profileUuid: String, contactUuid: String): ContactKeyMeta? {
        if (!CryptoFeatureFlag.isEnabled) return null
        val meta = loadContactMetadata(context, profileUuid, contactUuid) ?: return null
        val fp = meta.optString("publicKeyFingerprint", "")
        val keyChanged = if (fp.isNotEmpty()) {
            val rawKey = EncryptedStore.readBytes(context, File(contactDir(context, profileUuid, contactUuid), "public-key.bin"))
            rawKey != null && ExchangeBlob.fingerprint(rawKey) != fp
        } else false
        return ContactKeyMeta(
            contactUuid = contactUuid,
            displayName = meta.optString("displayName", contactUuid),
            keyEpoch = meta.optInt("keyEpoch", 1),
            status = meta.optString("verificationStatus", "unverified"),
            shortFingerprint = if (fp.length >= 8) "${fp.substring(0, 4)} ${fp.substring(4, 8)}" else fp,
            keyChanged = keyChanged
        )
    }

    fun deleteContactKey(context: Context, profileUuid: String, contactUuid: String) {
        if (!CryptoFeatureFlag.isEnabled) return
        val dir = contactDir(context, profileUuid, contactUuid)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun contactDir(context: Context, profileUuid: String, contactUuid: String): File =
        File(profileDir(context, profileUuid), "contacts/$contactUuid")

    private fun profileDir(context: Context, profileUuid: String): File =
        File(context.filesDir, "cyphr/profiles/$profileUuid")
}

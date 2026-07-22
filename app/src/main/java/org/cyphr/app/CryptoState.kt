package org.cyphr.app

import android.content.Context
import org.cyphr.app.crypto.ProfileKeyManager.ProfileInfo
import java.util.UUID

object CryptoState {
    const val DEFAULT_PROFILE_UUID = "default"

    @Volatile
    var viewModel: CryptoViewModel? = null

    val activeProfileUuid: String
        get() = viewModel?.activeProfileUuid ?: DEFAULT_PROFILE_UUID

    val profileKeysetBytes: ByteArray?
        get() = viewModel?.profileKeysetBytes

    val profilePublicKeyBytes: ByteArray?
        get() = viewModel?.profilePublicKeyBytes

    fun initialize(context: Context) {
        viewModel?.initialize(context)
    }

    fun listProfiles(): List<ProfileInfo> =
        viewModel?.listProfiles() ?: emptyList()

    fun switchProfile(context: Context, uuid: String): Boolean =
        viewModel?.switchProfile(context, uuid) ?: false

    fun createProfile(context: Context, uuid: String = UUID.randomUUID().toString(), displayName: String? = null): ProfileInfo? =
        viewModel?.createProfile(context, uuid, displayName)

    fun getMyFingerprint(): String? =
        viewModel?.getMyFingerprint()

    fun getMyShortFingerprint(): String? =
        viewModel?.getMyShortFingerprint()

    fun getMyExchangeBlob(): String? =
        viewModel?.getMyExchangeBlob()

    fun rotateKeys(context: Context): Boolean =
        viewModel?.rotateKeys(context) ?: false

    fun renameProfile(context: Context, uuid: String, newName: String): Boolean =
        viewModel?.renameProfile(context, uuid, newName) ?: false

    fun deleteProfile(context: Context, uuid: String): Boolean =
        viewModel?.deleteProfile(context, uuid) ?: false
}

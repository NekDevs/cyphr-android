package org.cyphr.app.crypto

import android.util.Log
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.util.Bytes

const val PAYLOAD_BEGIN_DELIMITER = "CY:"
const val PAYLOAD_END_DELIMITER = ":PHR"

object PayloadEncoder {

    private const val PAYLOAD_VERSION: Byte = 0x01
    private const val PAYLOAD_VERSION_V2: Byte = 0x02
    private const val REPLAY_COUNTER_SIZE_BYTES = 4
    const val PAYLOAD_SIZE_WARN_BYTES = 400
    private const val MAX_MESSAGE_SIZE_BYTES = 6000

    private val hpkeParams: HpkeParameters by lazy {
        HpkeParameters.builder()
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .build()
    }

    fun encodePayload(
        plaintextMessage: ByteArray,
        senderKeyEpoch: Int,
        recipientPublicKeyBytes: ByteArray,
        senderPublicKeyBytes: ByteArray? = null,
        replayCounter: Int = 0,
        contextInfo: ByteArray = "cyphr-c3-v1".toByteArray()
    ): String? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            if (plaintextMessage.size > MAX_MESSAGE_SIZE_BYTES) null
            else {
                val plaintext = assemblePlaintext(replayCounter, plaintextMessage)
                HybridConfig.register()
                val pubKey = HpkePublicKey.create(hpkeParams, Bytes.copyFrom(recipientPublicKeyBytes), null)
                val publicHandle = KeysetHandle.newBuilder()
                    .addEntry(KeysetHandle.importKey(pubKey).withRandomId().makePrimary())
                    .build()
                val hybridEncrypt = publicHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
                val ciphertext = hybridEncrypt.encrypt(plaintext, contextInfo)
                val wrapper = assembleOuterWrapper(senderKeyEpoch, ciphertext, senderPublicKeyBytes)
                val base64 = Base64UrlCodec.encode(wrapper)
                if (base64 != null) "$PAYLOAD_BEGIN_DELIMITER$base64$PAYLOAD_END_DELIMITER" else null
            }
        } catch (e: Exception) {
            Log.w("CyphrEncode", "encodePayload failed: ${e.message}")
            null
        }
    }

    fun assemblePlaintext(replayCounter: Int, message: ByteArray): ByteArray {
        val counterBytes = ByteArray(REPLAY_COUNTER_SIZE_BYTES)
        counterBytes[0] = (replayCounter shr 24).toByte()
        counterBytes[1] = (replayCounter shr 16).toByte()
        counterBytes[2] = (replayCounter shr 8).toByte()
        counterBytes[3] = replayCounter.toByte()
        return counterBytes + message
    }

    fun assembleOuterWrapper(keyEpoch: Int, ciphertext: ByteArray, senderPublicKeyBytes: ByteArray? = null): ByteArray {
        val epochBytes = ByteArray(2)
        epochBytes[0] = (keyEpoch shr 8).toByte()
        epochBytes[1] = keyEpoch.toByte()
        return if (senderPublicKeyBytes != null) {
            val keySize = senderPublicKeyBytes.size
            byteArrayOf(PAYLOAD_VERSION_V2, (keySize shr 8).toByte(), keySize.toByte()) +
                senderPublicKeyBytes + epochBytes + ciphertext
        } else {
            byteArrayOf(PAYLOAD_VERSION) + epochBytes + ciphertext
        }
    }
}

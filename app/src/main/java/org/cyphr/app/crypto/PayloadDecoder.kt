package org.cyphr.app.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig

data class DecodedPayload(
    val replayCounter: Int,
    val messageText: ByteArray,
    val keyEpoch: Int,
    val senderPublicKeyBytes: ByteArray? = null
)

data class ParsedOuterWrapper(
    val version: Byte,
    val senderKeyBytes: ByteArray?,
    val epoch: Int,
    val ciphertext: ByteArray
)

fun stripDelimiters(payload: String): String {
    val trimmed = payload.trim()
    return if (trimmed.startsWith(PAYLOAD_BEGIN_DELIMITER) && trimmed.endsWith(PAYLOAD_END_DELIMITER)) {
        trimmed
            .removePrefix(PAYLOAD_BEGIN_DELIMITER)
            .removeSuffix(PAYLOAD_END_DELIMITER)
            .trim()
    } else {
        trimmed
    }
}

object PayloadDecoder {

    private const val PAYLOAD_VERSION: Byte = 0x01
    private const val PAYLOAD_VERSION_V2: Byte = 0x02
    private const val MIN_WRAPPER_SIZE = 3
    private const val MIN_WRAPPER_SIZE_V2 = 37
    private const val REPLAY_COUNTER_SIZE_BYTES = 4
    private const val MAX_PAYLOAD_SIZE_BYTES = 24576

    fun decodePayload(
        encodedPayload: String,
        recipientKeysetBytes: ByteArray,
        contextInfo: ByteArray = "cyphr-c3-v1".toByteArray()
    ): DecodedPayload? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            val cleaned = stripDelimiters(encodedPayload)
            val rawPayload = Base64UrlCodec.decode(cleaned) ?: return null
            if (rawPayload.size > MAX_PAYLOAD_SIZE_BYTES) return null

            val parsed = parseOuterWrapper(rawPayload)
            if (!isVersionSupported(parsed.version)) return null

            HybridConfig.register()
            val handle = TinkProtoKeysetFormat.parseKeyset(
                recipientKeysetBytes,
                InsecureSecretKeyAccess.get(),
                RegistryConfiguration.get()
            )
            val hybridDecrypt = handle.getPrimitive(
                RegistryConfiguration.get(),
                HybridDecrypt::class.java
            )
            val plaintext = hybridDecrypt.decrypt(parsed.ciphertext, contextInfo)

            val counter = ((plaintext[0].toInt() and 0xFF) shl 24) or
                    ((plaintext[1].toInt() and 0xFF) shl 16) or
                    ((plaintext[2].toInt() and 0xFF) shl 8) or
                    (plaintext[3].toInt() and 0xFF)
            val message = plaintext.copyOfRange(REPLAY_COUNTER_SIZE_BYTES, plaintext.size)
            DecodedPayload(
                replayCounter = counter,
                messageText = message,
                keyEpoch = parsed.epoch,
                senderPublicKeyBytes = parsed.senderKeyBytes
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isVersionSupported(version: Byte): Boolean =
        version == PAYLOAD_VERSION || version == PAYLOAD_VERSION_V2

    fun parsePlaintext(plaintext: ByteArray): DecodedPayload? {
        if (plaintext.size < REPLAY_COUNTER_SIZE_BYTES) return null
        val counter = ((plaintext[0].toInt() and 0xFF) shl 24) or
                ((plaintext[1].toInt() and 0xFF) shl 16) or
                ((plaintext[2].toInt() and 0xFF) shl 8) or
                (plaintext[3].toInt() and 0xFF)
        val message = plaintext.copyOfRange(REPLAY_COUNTER_SIZE_BYTES, plaintext.size)
        return DecodedPayload(replayCounter = counter, messageText = message, keyEpoch = 0)
    }

    fun parseOuterWrapper(wrapper: ByteArray): ParsedOuterWrapper {
        require(wrapper.size >= 1) { "Wrapper is empty" }
        val version = wrapper[0]
        return when (version) {
            PAYLOAD_VERSION -> {
                require(wrapper.size >= MIN_WRAPPER_SIZE) { "Wrapper too short for v1: ${wrapper.size} bytes" }
                val epoch = ((wrapper[1].toInt() and 0xFF) shl 8) or (wrapper[2].toInt() and 0xFF)
                val ciphertext = wrapper.copyOfRange(MIN_WRAPPER_SIZE, wrapper.size)
                ParsedOuterWrapper(version, null, epoch, ciphertext)
            }
            PAYLOAD_VERSION_V2 -> {
                require(wrapper.size >= MIN_WRAPPER_SIZE_V2) { "Wrapper too short for v2: ${wrapper.size} bytes" }
                val keyLen = ((wrapper[1].toInt() and 0xFF) shl 8) or (wrapper[2].toInt() and 0xFF)
                val senderKey = wrapper.copyOfRange(3, 3 + keyLen)
                val epochStart = 3 + keyLen
                val epoch = ((wrapper[epochStart].toInt() and 0xFF) shl 8) or (wrapper[epochStart + 1].toInt() and 0xFF)
                val ciphertext = wrapper.copyOfRange(epochStart + 2, wrapper.size)
                ParsedOuterWrapper(version, senderKey, epoch, ciphertext)
            }
            else -> throw IllegalArgumentException("Unsupported version: ${version.toInt() and 0xFF}")
        }
    }
}

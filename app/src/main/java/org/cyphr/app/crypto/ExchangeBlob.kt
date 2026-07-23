package org.cyphr.app.crypto

import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object ExchangeBlob {

    private const val VERSION: Byte = 0x01
    const val ALGORITHM_TAG = "HPKE-X25519-AES256GCM"

    fun build(publicKeyBytes: ByteArray, keyEpoch: Int): String {
        require(keyEpoch in 0..65535) { "keyEpoch must fit in uint16, got $keyEpoch" }
        val tagBytes = ALGORITHM_TAG.toByteArray(Charsets.UTF_8)
        val raw = ByteArray(1 + 1 + tagBytes.size + 2 + 2 + publicKeyBytes.size)
        var off = 0
        raw[off++] = VERSION
        raw[off++] = tagBytes.size.toByte()
        tagBytes.copyInto(raw, off); off += tagBytes.size
        raw[off++] = (keyEpoch shr 8).toByte()
        raw[off++] = keyEpoch.toByte()
        raw[off++] = (publicKeyBytes.size shr 8).toByte()
        raw[off++] = publicKeyBytes.size.toByte()
        publicKeyBytes.copyInto(raw, off)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class ParsedBlob(
        val version: Int,
        val algorithmTag: String,
        val keyEpoch: Int,
        val publicKeyBytes: ByteArray
    )

    fun parse(encoded: String): ParsedBlob? {
        return try {
            val raw = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING)
            if (raw.size < 6) return null
            var off = 0
            val version = raw[off++].toInt() and 0xFF
            if (version != 1) return null
            val tagLen = raw[off++].toInt() and 0xFF
            if (raw.size < off + tagLen + 4) return null
            val tag = raw.copyOfRange(off, off + tagLen).decodeToString()
            off += tagLen
            val epoch = ((raw[off].toInt() and 0xFF) shl 8) or (raw[off + 1].toInt() and 0xFF)
            off += 2
            val keyLen = ((raw[off].toInt() and 0xFF) shl 8) or (raw[off + 1].toInt() and 0xFF)
            if (raw.size < off + 2 + keyLen) return null
            off += 2
            val key = raw.copyOfRange(off, off + keyLen)
            ParsedBlob(version, tag, epoch, key)
        } catch (e: Exception) {
            Log.w("CyphrBlob", "parse failed: ${e.message}")
            null
        }
    }

    fun fingerprint(publicKeyBytes: ByteArray): String {
        val data = ALGORITHM_TAG.toByteArray(Charsets.UTF_8) + publicKeyBytes
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
        return hash.joinToString("") { "%02X".format(it) }
    }

    fun shortFingerprint(publicKeyBytes: ByteArray): String {
        val f = fingerprint(publicKeyBytes)
        return "${f.substring(0, 4)} ${f.substring(4, 8)}"
    }

}

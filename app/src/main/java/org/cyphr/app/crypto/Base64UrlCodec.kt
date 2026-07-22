package org.cyphr.app.crypto

import android.util.Base64

object Base64UrlCodec {

    fun encode(data: ByteArray): String? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(encoded: String): ByteArray? {
        if (!CryptoFeatureFlag.isEnabled) return null
        return try {
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

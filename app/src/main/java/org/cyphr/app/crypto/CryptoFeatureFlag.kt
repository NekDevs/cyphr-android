package org.cyphr.app.crypto

object CryptoFeatureFlag {
    @Volatile
    var isEnabled: Boolean = true
        private set

    fun enable() { isEnabled = true }
    fun disable() { isEnabled = false }
}

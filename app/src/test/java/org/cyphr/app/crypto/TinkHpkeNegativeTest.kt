package org.cyphr.app.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.util.Bytes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

class TinkHpkeNegativeTest {

    private lateinit var senderHandle: KeysetHandle
    private lateinit var recipientHandle: KeysetHandle
    private lateinit var ciphertext: ByteArray
    private val contextInfo = "cyphr-c3-v1".toByteArray()
    private val plaintext = "secret message".toByteArray()

    private val params: HpkeParameters by lazy {
        HpkeParameters.builder()
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .build()
    }

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        HybridConfig.register()

        recipientHandle = KeysetHandle.generateNew(params)
        val pubKeyBytes = (recipientHandle.getPrimary().getKey()
                as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()

        val pubKey = HpkePublicKey.create(params, Bytes.copyFrom(pubKeyBytes), null)
        senderHandle = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(pubKey).withRandomId().makePrimary())
            .build()

        val encrypt = senderHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridEncrypt::class.java
        )
        ciphertext = encrypt.encrypt(plaintext, contextInfo)
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun decryptWithWrongContextInfoFails() {
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val wrongContext = "wrong-context".toByteArray()
        try {
            decryptor.decrypt(ciphertext, wrongContext)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithWrongKeyFails() {
        val wrongHandle = KeysetHandle.generateNew(params)
        val decryptor = wrongHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        try {
            decryptor.decrypt(ciphertext, contextInfo)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithModifiedEncFails() {
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val modified = ciphertext.copyOf()
        modified[0] = (modified[0].toInt() xor 0x01).toByte()
        try {
            decryptor.decrypt(modified, contextInfo)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithModifiedCiphertextByteFails() {
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val modified = ciphertext.copyOf()
        if (modified.size > 40) {
            modified[40] = (modified[40].toInt() xor 0xFF).toByte()
        }
        try {
            decryptor.decrypt(modified, contextInfo)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithModifiedLastByteFails() {
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val modified = ciphertext.copyOf()
        modified[modified.size - 1] = (modified.last().toInt() xor 0x01).toByte()
        try {
            decryptor.decrypt(modified, contextInfo)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithTruncatedCiphertextFails() {
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val truncated = ciphertext.copyOfRange(0, ciphertext.size - 1)
        try {
            decryptor.decrypt(truncated, contextInfo)
            fail("Expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun encryptDecryptEmptyPlaintextSucceeds() {
        val encrypt = senderHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridEncrypt::class.java
        )
        val ct = encrypt.encrypt(byteArrayOf(), contextInfo)
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val pt = decryptor.decrypt(ct, contextInfo)
        assertArrayEquals(byteArrayOf(), pt)
    }

    @Test
    fun encryptDecryptWithEmptyContextInfoSucceeds() {
        val encrypt = senderHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridEncrypt::class.java
        )
        val ct = encrypt.encrypt(plaintext, byteArrayOf())
        val decryptor = recipientHandle.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java
        )
        val pt = decryptor.decrypt(ct, byteArrayOf())
        assertArrayEquals(plaintext, pt)
    }
}

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

class PayloadNegativeTest {

    private lateinit var recipientHandle: KeysetHandle
    private lateinit var keysetBytes: ByteArray
    private lateinit var publicKeyBytes: ByteArray
    private val contextInfo = "cyphr-c3-v1".toByteArray()

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
        val privKey = recipientHandle.getPrimary().getKey() as HpkePrivateKey
        publicKeyBytes = privKey.publicKey.publicKeyBytes.toByteArray()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun parseOuterWrapperRejectsShortInput() {
        val tooShort = ByteArray(2) { 0x00.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            PayloadDecoder.parseOuterWrapper(tooShort)
        }
    }

    @Test
    fun parseOuterWrapperRejectsEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) {
            PayloadDecoder.parseOuterWrapper(byteArrayOf())
        }
    }

    @Test
    fun parseOuterWrapperRejectsV2ShortInput() {
        val tooShort = byteArrayOf(0x02, 0x00, 0x20, 0x01, 0x02)
        assertThrows(IllegalArgumentException::class.java) {
            PayloadDecoder.parseOuterWrapper(tooShort)
        }
    }

    @Test
    fun parseOuterWrapperRejectsV2ZeroKeyLen() {
        val wrapper = byteArrayOf(0x02, 0x00, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            PayloadDecoder.parseOuterWrapper(wrapper)
        }
    }

    @Test
    fun parseOuterWrapperRejectsV2KeyLenExceedsWrapper() {
        val keyLen = 50
        val buf = ByteArray(1 + 2 + keyLen)
        buf[0] = 0x02
        buf[1] = (keyLen shr 8).toByte()
        buf[2] = keyLen.toByte()
        try {
            PayloadDecoder.parseOuterWrapper(buf)
            fail("Expected exception for v2 wrapper with keyLen exceeding available data")
        } catch (_: IllegalArgumentException) {
        } catch (_: ArrayIndexOutOfBoundsException) {
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    @Test
    fun isVersionSupportedRejectsV0() {
        assertFalse(PayloadDecoder.isVersionSupported(0x00))
    }

    @Test
    fun isVersionSupportedRejectsV255() {
        assertFalse(PayloadDecoder.isVersionSupported(0xFF.toByte()))
    }

    @Test
    fun isVersionSupportedAcceptsV2() {
        assertTrue(PayloadDecoder.isVersionSupported(0x02))
    }

    @Test
    fun decryptWithWrongKeyFails() {
        val wrongHandle = KeysetHandle.generateNew(params)
        val wrongPubBytes = (wrongHandle.getPrimary().getKey() as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()
        val wrongPub = HpkePublicKey.create(params, Bytes.copyFrom(wrongPubBytes), null)
        val wrongSender = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(wrongPub).withRandomId().makePrimary())
            .build()
        val encrypt = wrongSender.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val ct = encrypt.encrypt("test".toByteArray(), contextInfo)

        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        try {
            decrypt.decrypt(ct, contextInfo)
            fail("Expected GeneralSecurityException for wrong key")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun decryptWithWrongContextInfoFails() {
        val pubKey = HpkePublicKey.create(params, Bytes.copyFrom(publicKeyBytes), null)
        val sender = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(pubKey).withRandomId().makePrimary())
            .build()
        val encrypt = sender.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val ct = encrypt.encrypt("test".toByteArray(), contextInfo)

        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        try {
            decrypt.decrypt(ct, "wrong-context".toByteArray())
            fail("Expected GeneralSecurityException for wrong contextInfo")
        } catch (_: GeneralSecurityException) {
        }
    }
}

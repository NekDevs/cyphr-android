package org.cyphr.app.crypto

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KeyGenerationTest {

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
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun generateHandleReturnsNonNull() {
        val handle = KeysetHandle.generateNew(params)
        assertNotNull(handle)
    }

    @Test
    fun handleContainsSingleEnabledKey() {
        val handle = KeysetHandle.generateNew(params)
        assertEquals(HpkePrivateKey::class, handle.getPrimary().getKey()::class)
    }

    @Test
    fun publicKeyIs32Bytes() {
        val handle = KeysetHandle.generateNew(params)
        val privKey = handle.getPrimary().getKey() as HpkePrivateKey
        val pubKeyBytes = privKey.publicKey.publicKeyBytes.toByteArray()
        assertEquals(32, pubKeyBytes.size)
    }

    @Test
    fun publicKeyIsIdempotent() {
        val handle = KeysetHandle.generateNew(params)
        val privKey = handle.getPrimary().getKey() as HpkePrivateKey
        val first = privKey.publicKey.publicKeyBytes.toByteArray()
        val second = privKey.publicKey.publicKeyBytes.toByteArray()
        assertArrayEquals(first, second)
    }

    @Test
    fun differentGenerationsProduceDifferentKeys() {
        val handleA = KeysetHandle.generateNew(params)
        val handleB = KeysetHandle.generateNew(params)
        val pubA = (handleA.getPrimary().getKey() as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()
        val pubB = (handleB.getPrimary().getKey() as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()
        assertFalse(pubA.contentEquals(pubB))
    }

    @Test
    fun keysetRoundTripsThroughSerialisation() {
        val original = KeysetHandle.generateNew(params)
        val serialised = TinkProtoKeysetFormat.serializeKeyset(
            original, InsecureSecretKeyAccess.get(), RegistryConfiguration.get()
        )
        val restored = TinkProtoKeysetFormat.parseKeyset(
            serialised, InsecureSecretKeyAccess.get(), RegistryConfiguration.get()
        )
        val origPub = (original.getPrimary().getKey() as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()
        val restPub = (restored.getPrimary().getKey() as HpkePrivateKey)
            .publicKey.publicKeyBytes.toByteArray()
        assertArrayEquals(origPub, restPub)
    }

    @Test
    fun getPublicKeyReturnsNullWhenFlagIsDisabled() {
        CryptoFeatureFlag.disable()
        assertNull(ProfileKeyManager.getPublicKey(ByteArray(32)))
    }

    @Test
    fun getPublicKeyReturnsNullOnInvalidInput() {
        CryptoFeatureFlag.enable()
        assertNull(ProfileKeyManager.getPublicKey(ByteArray(4)))
    }

    @Test
    fun getPublicKeyExtracts32Bytes() {
        val handle = KeysetHandle.generateNew(params)
        val serialised = TinkProtoKeysetFormat.serializeKeyset(
            handle, InsecureSecretKeyAccess.get(), RegistryConfiguration.get()
        )
        val pubKey = ProfileKeyManager.getPublicKey(serialised)
        assertNotNull(pubKey)
        assertEquals(32, pubKey!!.size)
    }
}

package org.cyphr.app.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.util.Bytes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PayloadRoundTripTest {

    private lateinit var recipientHandle: KeysetHandle
    private lateinit var senderHandle: KeysetHandle
    private lateinit var publicKeyBytes: ByteArray
    private lateinit var keysetBytes: ByteArray
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

        keysetBytes = TinkProtoKeysetFormat.serializeKeyset(
            recipientHandle, InsecureSecretKeyAccess.get(), RegistryConfiguration.get()
        )

        val pubKey = HpkePublicKey.create(params, Bytes.copyFrom(publicKeyBytes), null)
        senderHandle = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(pubKey).withRandomId().makePrimary())
            .build()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun tinkEncryptDecryptEmptyMessage() {
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)

        val ct = encrypt.encrypt(byteArrayOf(), contextInfo)
        val pt = decrypt.decrypt(ct, contextInfo)
        assertArrayEquals(byteArrayOf(), pt)
    }

    @Test
    fun tinkEncryptDecryptHelloWorld() {
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val message = "Hello, World!".toByteArray()

        val ct = encrypt.encrypt(message, contextInfo)
        val pt = decrypt.decrypt(ct, contextInfo)
        assertArrayEquals(message, pt)
    }

    @Test
    fun tinkEncryptDecrypt255ByteMessage() {
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val message = ByteArray(255) { it.toByte() }

        val ct = encrypt.encrypt(message, contextInfo)
        val pt = decrypt.decrypt(ct, contextInfo)
        assertArrayEquals(message, pt)
    }

    @Test
    fun tinkEncryptDecrypt4096ByteMessage() {
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val message = ByteArray(4096) { it.toByte() }

        val ct = encrypt.encrypt(message, contextInfo)
        val pt = decrypt.decrypt(ct, contextInfo)
        assertArrayEquals(message, pt)
    }

    @Test
    fun encodePayloadRejectsMessageOver6000Bytes() {
        val largeMessage = ByteArray(6001) { 0x41.toByte() }
        assertNull(
            PayloadEncoder.encodePayload(
                plaintextMessage = largeMessage,
                senderKeyEpoch = 1,
                recipientPublicKeyBytes = publicKeyBytes
            )
        )
    }

    @Test
    fun encodePayloadRejects6001ByteExactOverBoundary() {
        val tooLarge = ByteArray(6001) { 0x42.toByte() }
        assertNull(
            PayloadEncoder.encodePayload(
                plaintextMessage = tooLarge,
                senderKeyEpoch = 1,
                recipientPublicKeyBytes = publicKeyBytes
            )
        )
    }

    @Test
    fun encodeReturnsNullWhenFlagIsDisabled() {
        CryptoFeatureFlag.disable()
        assertNull(
            PayloadEncoder.encodePayload(
                plaintextMessage = "test".toByteArray(),
                senderKeyEpoch = 1,
                recipientPublicKeyBytes = publicKeyBytes
            )
        )
    }

    @Test
    fun decodeReturnsNullWhenFlagIsDisabled() {
        CryptoFeatureFlag.disable()
        assertNull(
            PayloadDecoder.decodePayload(
                encodedPayload = "",
                recipientKeysetBytes = keysetBytes
            )
        )
    }

    @Test
    fun outerWrapperAssembleParseRoundTripV1() {
        val ciphertext = ByteArray(52) { it.toByte() }
        val wrapper = PayloadEncoder.assembleOuterWrapper(42, ciphertext)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x01.toByte(), parsed.version)
        assertNull(parsed.senderKeyBytes)
        assertEquals(42, parsed.epoch)
        assertArrayEquals(ciphertext, parsed.ciphertext)
    }

    @Test
    fun outerWrapperAssembleParseRoundTripV2() {
        val ciphertext = ByteArray(52) { it.toByte() }
        val senderKey = ByteArray(32) { (it + 1).toByte() }
        val wrapper = PayloadEncoder.assembleOuterWrapper(99, ciphertext, senderKey)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x02.toByte(), parsed.version)
        assertArrayEquals(senderKey, parsed.senderKeyBytes)
        assertEquals(99, parsed.epoch)
        assertArrayEquals(ciphertext, parsed.ciphertext)
    }

    @Test
    fun encodeDecodeRoundTripV2WithSenderKey() {
        val senderKey = ByteArray(32) { (it + 10).toByte() }
        val message = "Hello v2".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(0, message)
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val ciphertext = encrypt.encrypt(plaintext, contextInfo)
        val wrapper = PayloadEncoder.assembleOuterWrapper(5, ciphertext, senderKey)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x02.toByte(), parsed.version)
        assertArrayEquals(senderKey, parsed.senderKeyBytes)
        assertEquals(5, parsed.epoch)
        val recovered = decrypt.decrypt(parsed.ciphertext, contextInfo)
        val readMessage = recovered.copyOfRange(4, recovered.size)
        assertArrayEquals(message, readMessage)
    }

    @Test
    fun encodeDecodeRoundTripV1NoSenderKey() {
        val message = "Hello v1".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(0, message)
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val ciphertext = encrypt.encrypt(plaintext, contextInfo)
        val wrapper = PayloadEncoder.assembleOuterWrapper(3, ciphertext)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x01.toByte(), parsed.version)
        assertNull(parsed.senderKeyBytes)
        assertEquals(3, parsed.epoch)
        val recovered = decrypt.decrypt(parsed.ciphertext, contextInfo)
        val readMessage = recovered.copyOfRange(4, recovered.size)
        assertArrayEquals(message, readMessage)
    }

    @Test
    fun decodePayloadV1ReturnsNullSenderKey() {
        val message = "v1 null sender".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(7, message)
        val encrypt = senderHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val ciphertext = encrypt.encrypt(plaintext, contextInfo)
        val wrapper = PayloadEncoder.assembleOuterWrapper(2, ciphertext)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x01.toByte(), parsed.version)
        assertNull(parsed.senderKeyBytes)
        val decrypt = recipientHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        val recovered = decrypt.decrypt(parsed.ciphertext, contextInfo)
        val readMessage = recovered.copyOfRange(4, recovered.size)
        assertArrayEquals(message, readMessage)
    }

    @Test
    fun plaintextAssembleParseRoundTrip() {
        val plaintext = PayloadEncoder.assemblePlaintext(12345, "test message".toByteArray())
        val decoded = PayloadDecoder.parsePlaintext(plaintext)!!
        assertEquals(12345, decoded.replayCounter)
        assertArrayEquals("test message".toByteArray(), decoded.messageText)
    }

    @Test
    fun stripDelimitersStripsArmor() {
        val inner = "dGVzdC1iYXNlNjQ"
        val armored = "$PAYLOAD_BEGIN_DELIMITER\n$inner\n$PAYLOAD_END_DELIMITER"
        assertEquals(inner, stripDelimiters(armored))
    }

    @Test
    fun stripDelimitersPassesThroughBareInput() {
        val bare = "dGVzdC1iYXNlNjQ"
        assertEquals(bare, stripDelimiters(bare))
    }

    @Test
    fun stripDelimitersHandlesWhitespace() {
        val inner = "dGVzdC1iYXNlNjQ"
        val armored = "  $PAYLOAD_BEGIN_DELIMITER\n$inner\n$PAYLOAD_END_DELIMITER  "
        assertEquals(inner, stripDelimiters(armored))
    }
}

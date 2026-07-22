package org.cyphr.app.crypto

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReplayDetectionNegativeTest {

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
    }

    @Test
    fun assemblePlaintextEncodesCounterCorrectly() {
        val message = "hello".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(1, message)
        assertEquals(4 + message.size, plaintext.size)

        val counter = ((plaintext[0].toInt() and 0xFF) shl 24) or
                ((plaintext[1].toInt() and 0xFF) shl 16) or
                ((plaintext[2].toInt() and 0xFF) shl 8) or
                (plaintext[3].toInt() and 0xFF)
        assertEquals(1, counter)
    }

    @Test
    fun assemblePlaintextMaxCounter() {
        val message = "test".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(Int.MAX_VALUE, message)
        val counter = ((plaintext[0].toInt() and 0xFF) shl 24) or
                ((plaintext[1].toInt() and 0xFF) shl 16) or
                ((plaintext[2].toInt() and 0xFF) shl 8) or
                (plaintext[3].toInt() and 0xFF)
        assertEquals(Int.MAX_VALUE, counter)
    }

    @Test
    fun parsePlaintextExtractsCounterCorrectly() {
        val message = "hello world".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(42, message)
        val decoded = PayloadDecoder.parsePlaintext(plaintext)!!
        assertEquals(42, decoded.replayCounter)
        assertArrayEquals(message, decoded.messageText)
    }

    @Test
    fun parsePlaintextZeroCounter() {
        val message = "zero".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(0, message)
        val decoded = PayloadDecoder.parsePlaintext(plaintext)!!
        assertEquals(0, decoded.replayCounter)
        assertArrayEquals(message, decoded.messageText)
    }

    @Test
    fun parsePlaintextMaxCounter() {
        val message = "max".toByteArray()
        val plaintext = PayloadEncoder.assemblePlaintext(Int.MAX_VALUE, message)
        val decoded = PayloadDecoder.parsePlaintext(plaintext)!!
        assertEquals(Int.MAX_VALUE, decoded.replayCounter)
    }

    @Test
    fun assembleOuterWrapperEncodesEpochCorrectly() {
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03)
        val wrapper = PayloadEncoder.assembleOuterWrapper(1, ciphertext)
        assertEquals(1 + 2 + ciphertext.size, wrapper.size)
        assertEquals(0x01.toByte(), wrapper[0])
        assertEquals(0, wrapper[1].toInt() and 0xFF)
        assertEquals(1, wrapper[2].toInt() and 0xFF)
    }

    @Test
    fun assembleOuterWrapperV2Format() {
        val ciphertext = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val senderKey = ByteArray(32) { it.toByte() }
        val wrapper = PayloadEncoder.assembleOuterWrapper(0x0102, ciphertext, senderKey)
        assertEquals(1 + 2 + 32 + 2 + 2, wrapper.size)
        assertEquals(0x02.toByte(), wrapper[0])
        assertEquals(0, wrapper[1].toInt() and 0xFF)
        assertEquals(32, wrapper[2].toInt() and 0xFF)
        for (i in 0 until 32) assertEquals(i.toByte(), wrapper[3 + i])
        assertEquals(0x01, wrapper[35].toInt() and 0xFF)
        assertEquals(0x02, wrapper[36].toInt() and 0xFF)
        assertEquals(0xAA.toByte(), wrapper[37])
        assertEquals(0xBB.toByte(), wrapper[38])
    }

    @Test
    fun parseOuterWrapperExtractsFieldsCorrectly() {
        val ciphertext = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val wrapper = PayloadEncoder.assembleOuterWrapper(257, ciphertext)
        val parsed = PayloadDecoder.parseOuterWrapper(wrapper)
        assertEquals(0x01.toByte(), parsed.version)
        assertNull(parsed.senderKeyBytes)
        assertEquals(257, parsed.epoch)
        assertArrayEquals(ciphertext, parsed.ciphertext)
    }

    @Test
    fun parseOuterWrapperRejectsShortInput() {
        val tooShort = byteArrayOf(0x01, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            PayloadDecoder.parseOuterWrapper(tooShort)
        }
    }

    @Test
    fun isVersionSupportedAcceptsV1() {
        assertTrue(PayloadDecoder.isVersionSupported(0x01))
    }

    @Test
    fun isVersionSupportedRejectsOtherVersions() {
        assertFalse(PayloadDecoder.isVersionSupported(0x00))
        assertFalse(PayloadDecoder.isVersionSupported(0xFF.toByte()))
        assertTrue(PayloadDecoder.isVersionSupported(0x02))
    }

    @Test
    fun counterReplaySimulation() {
        var storedReceiveCounter = 0

        fun acceptPayload(counter: Int): Boolean {
            if (counter <= storedReceiveCounter) return false
            storedReceiveCounter = counter
            return true
        }

        assertTrue(acceptPayload(1))
        assertFalse(acceptPayload(1))
        assertTrue(acceptPayload(5))
        assertFalse(acceptPayload(3))
        assertFalse(acceptPayload(5))
        assertTrue(acceptPayload(6))
    }

    @Test
    fun counterAcceptsAfterReset() {
        var storedReceiveCounter = 0
        fun acceptPayload(counter: Int): Boolean {
            if (counter <= storedReceiveCounter) return false
            storedReceiveCounter = counter
            return true
        }

        assertTrue(acceptPayload(3))
        storedReceiveCounter = 0
        assertTrue(acceptPayload(2))
    }
}

package org.cyphr.app.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageLogStoreTest {

    private lateinit var context: Context
    private val profileUuid = "test-profile"

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        MessageLogStore.clearProfile(context, profileUuid)
        CryptoFeatureFlag.disable()
    }

    @Test
    fun saveAndLoadSingleMessage() {
        val msg = StoredMessage(
            messageId = "msg-1",
            profileUuid = profileUuid,
            senderContactUuid = "contact-1",
            senderDisplayName = "Alice",
            senderFingerprint = "ABCD 1234",
            replayCounter = 0,
            messageText = "hello",
            rawPayload = "CY:abc:def:PHR",
            decryptedAt = "2024-01-01T00:00:00Z",
            keyEpoch = 1
        )
        MessageLogStore.saveMessage(context, msg)
        val messages = MessageLogStore.loadMessages(context, profileUuid)
        assertEquals(1, messages.size)
        val loaded = messages.first()
        assertEquals(msg.messageId, loaded.messageId)
        assertEquals(msg.messageText, loaded.messageText)
        assertEquals(msg.rawPayload, loaded.rawPayload)
        assertEquals(msg.replayCounter, loaded.replayCounter)
        assertFalse(loaded.isOutgoing)
    }

    @Test
    fun loadMessagesReturnsEmptyForNewProfile() {
        val messages = MessageLogStore.loadMessages(context, profileUuid)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun saveAndLoadOutgoingMessage() {
        val msg = StoredMessage(
            messageId = "msg-out",
            profileUuid = profileUuid,
            senderContactUuid = "contact-2",
            senderDisplayName = null,
            senderFingerprint = null,
            replayCounter = 5,
            messageText = "outgoing text",
            rawPayload = "CY:xyz:PHR",
            decryptedAt = "2024-02-01T12:00:00Z",
            keyEpoch = 2,
            isOutgoing = true
        )
        MessageLogStore.saveMessage(context, msg)
        val loaded = MessageLogStore.loadMessages(context, profileUuid).first()
        assertTrue(loaded.isOutgoing)
        assertNull(loaded.senderDisplayName)
    }

    @Test
    fun messagesAreOrderedByDecryptedAtDesc() {
        val early = StoredMessage("m1", profileUuid, null, null, null, 0, "first", "", "2024-01-01T00:00:00Z", 1)
        val late = StoredMessage("m2", profileUuid, null, null, null, 0, "second", "", "2024-06-01T00:00:00Z", 1)
        MessageLogStore.saveMessage(context, early)
        MessageLogStore.saveMessage(context, late)
        val messages = MessageLogStore.loadMessages(context, profileUuid)
        assertEquals(2, messages.size)
        assertEquals("m2", messages[0].messageId)
        assertEquals("m1", messages[1].messageId)
    }

    @Test
    fun deleteMessageRemovesIt() {
        val msg = StoredMessage("to-delete", profileUuid, null, null, null, 0, "text", "", "2024-01-01T00:00:00Z", 1)
        MessageLogStore.saveMessage(context, msg)
        assertEquals(1, MessageLogStore.loadMessages(context, profileUuid).size)
        MessageLogStore.deleteMessage(context, profileUuid, "to-delete")
        assertEquals(0, MessageLogStore.loadMessages(context, profileUuid).size)
    }

    @Test
    fun clearProfileRemovesAllMessages() {
        MessageLogStore.saveMessage(context, StoredMessage("a", profileUuid, null, null, null, 0, "a", "", "2024-01-01T00:00:00Z", 1))
        MessageLogStore.saveMessage(context, StoredMessage("b", profileUuid, null, null, null, 0, "b", "", "2024-01-02T00:00:00Z", 1))
        assertEquals(2, MessageLogStore.loadMessages(context, profileUuid).size)
        MessageLogStore.clearProfile(context, profileUuid)
        assertTrue(MessageLogStore.loadMessages(context, profileUuid).isEmpty())
    }
}

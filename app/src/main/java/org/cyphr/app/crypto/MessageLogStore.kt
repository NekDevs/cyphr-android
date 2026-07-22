package org.cyphr.app.crypto

import android.content.Context
import org.json.JSONObject
import java.io.File
private fun JSONObject.optStringOrNull(name: String): String? {
    val value = optString(name, "")
    return if (value.isEmpty()) null else value
}

data class StoredMessage(
    val messageId: String,
    val profileUuid: String,
    val senderContactUuid: String?,
    val senderDisplayName: String?,
    val senderFingerprint: String?,
    val replayCounter: Int,
    val messageText: String,
    val rawPayload: String,
    val decryptedAt: String,
    val keyEpoch: Int,
    val isOutgoing: Boolean = false
)

object MessageLogStore {

    private fun messagesDir(context: Context, profileUuid: String): File =
        File(context.filesDir, "cyphr/profiles/$profileUuid/messages")

    fun saveMessage(context: Context, message: StoredMessage) {
        if (!CryptoFeatureFlag.isEnabled) return
        val dir = messagesDir(context, message.profileUuid)
        dir.mkdirs()
        val json = JSONObject().apply {
            put("messageId", message.messageId)
            put("profileUuid", message.profileUuid)
            put("senderContactUuid", message.senderContactUuid ?: JSONObject.NULL)
            put("senderDisplayName", message.senderDisplayName ?: JSONObject.NULL)
            put("senderFingerprint", message.senderFingerprint ?: JSONObject.NULL)
            put("replayCounter", message.replayCounter)
            put("messageText", message.messageText)
            put("rawPayload", message.rawPayload)
            put("decryptedAt", message.decryptedAt)
            put("keyEpoch", message.keyEpoch)
            put("isOutgoing", message.isOutgoing)
        }
        EncryptedStore.writeText(context, File(dir, "${message.messageId}.json"), json.toString())
    }

    fun loadMessages(context: Context, profileUuid: String): List<StoredMessage> {
        if (!CryptoFeatureFlag.isEnabled) return emptyList()
        val dir = messagesDir(context, profileUuid)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    val text = EncryptedStore.readText(context, file) ?: return@mapNotNull null
                    val json = JSONObject(text)
                    StoredMessage(
                        messageId = json.getString("messageId"),
                        profileUuid = json.getString("profileUuid"),
                        senderContactUuid = json.optStringOrNull("senderContactUuid"),
                        senderDisplayName = json.optStringOrNull("senderDisplayName"),
                        senderFingerprint = json.optStringOrNull("senderFingerprint"),
                        replayCounter = json.getInt("replayCounter"),
                        messageText = json.getString("messageText"),
                        rawPayload = json.getString("rawPayload"),
                        decryptedAt = json.getString("decryptedAt"),
                        keyEpoch = json.getInt("keyEpoch"),
                        isOutgoing = json.optBoolean("isOutgoing", false)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.decryptedAt }
            ?: emptyList()
    }

    fun deleteMessage(context: Context, profileUuid: String, messageId: String) {
        if (!CryptoFeatureFlag.isEnabled) return
        val file = File(messagesDir(context, profileUuid), "$messageId.json")
        if (file.exists()) file.delete()
    }

    fun clearProfile(context: Context, profileUuid: String) {
        if (!CryptoFeatureFlag.isEnabled) return
        val dir = messagesDir(context, profileUuid)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}

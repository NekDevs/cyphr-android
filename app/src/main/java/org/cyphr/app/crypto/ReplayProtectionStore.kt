package org.cyphr.app.crypto

import android.content.Context
import android.util.Log
import org.cyphr.app.crypto.EncryptedStoreException
import org.json.JSONObject
import java.io.File

data class SendReceiveCounters(
    val sendCounter: Int,
    val receiveCounter: Int
)

class ReplayProtectionStore(private val context: Context) {

    @Synchronized
    fun loadCounters(profileUuid: String, contactUuid: String): SendReceiveCounters {
        if (!CryptoFeatureFlag.isEnabled) return SendReceiveCounters(0, -1)
        val file = countersFile(profileUuid, contactUuid)
        val text = EncryptedStore.readText(context, file)
        if (text == null) return SendReceiveCounters(0, -1)
        return try {
            val json = JSONObject(text)
            SendReceiveCounters(
                sendCounter = json.optInt("sendCounter", 0),
                receiveCounter = json.optInt("receiveCounter", -1)
            )
        } catch (_: Exception) {
            Log.w("CyphrReplay", "loadCounters JSON parse failed")
            SendReceiveCounters(0, -1)
        }
    }

    @Synchronized
    fun saveCounters(profileUuid: String, contactUuid: String, counters: SendReceiveCounters) {
        if (!CryptoFeatureFlag.isEnabled) return
        val file = countersFile(profileUuid, contactUuid)
        val json = JSONObject().apply {
            put("sendCounter", counters.sendCounter)
            put("receiveCounter", counters.receiveCounter)
        }
        try {
            EncryptedStore.writeText(context, file, json.toString())
        } catch (e: EncryptedStoreException) {
            Log.w("CyphrReplay", "saveCounters failed: ${e.message}")
        }
    }

    @Synchronized
    fun acceptReceivedCounter(profileUuid: String, contactUuid: String, counter: Int): Boolean {
        if (!CryptoFeatureFlag.isEnabled) return false
        if (counter < 0) return false
        val current = loadCounters(profileUuid, contactUuid)
        if (counter <= current.receiveCounter) return false
        saveCounters(profileUuid, contactUuid, current.copy(receiveCounter = counter))
        return true
    }

    @Synchronized
    fun nextSendCounter(profileUuid: String, contactUuid: String): Int {
        val current = loadCounters(profileUuid, contactUuid)
        val next = if (current.sendCounter >= Int.MAX_VALUE - 1) {
            Log.w("CyphrReplay", "send counter overflow, wrapping to 0")
            0
        } else {
            current.sendCounter + 1
        }
        saveCounters(profileUuid, contactUuid, current.copy(sendCounter = next))
        return current.sendCounter
    }

    private fun baseDir(profileUuid: String, contactUuid: String): File =
        File(context.filesDir, "cyphr/profiles/$profileUuid/contacts/$contactUuid")

    private fun countersFile(profileUuid: String, contactUuid: String): File =
        File(baseDir(profileUuid, contactUuid), "counters.json")
}

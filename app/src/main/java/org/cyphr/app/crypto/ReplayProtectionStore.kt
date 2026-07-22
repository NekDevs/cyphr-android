package org.cyphr.app.crypto

import android.content.Context
import org.json.JSONObject
import java.io.File

data class SendReceiveCounters(
    val sendCounter: Int,
    val receiveCounter: Int
)

class ReplayProtectionStore(private val context: Context) {

    @Synchronized
    fun loadCounters(profileUuid: String, contactUuid: String): SendReceiveCounters {
        if (!CryptoFeatureFlag.isEnabled) return SendReceiveCounters(0, 0)
        val file = countersFile(profileUuid, contactUuid)
        val text = EncryptedStore.readText(context, file)
        if (text == null) return SendReceiveCounters(0, 0)
        return try {
            val json = JSONObject(text)
            SendReceiveCounters(
                sendCounter = json.optInt("sendCounter", 0),
                receiveCounter = json.optInt("receiveCounter", 0)
            )
        } catch (_: Exception) {
            SendReceiveCounters(0, 0)
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
        EncryptedStore.writeText(context, file, json.toString())
    }

    /**
     * Validates that [counter] is newer than the stored receive counter for the
     * given contact, and updates the stored counter if valid.
     *
     * Returns `true` if the counter is accepted, `false` if it is a replay or
     * otherwise invalid.
     */
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
        saveCounters(profileUuid, contactUuid, current.copy(sendCounter = current.sendCounter + 1))
        return current.sendCounter
    }

    private fun baseDir(profileUuid: String, contactUuid: String): File =
        File(context.filesDir, "cyphr/profiles/$profileUuid/contacts/$contactUuid")

    private fun countersFile(profileUuid: String, contactUuid: String): File =
        File(baseDir(profileUuid, contactUuid), "counters.json")
}

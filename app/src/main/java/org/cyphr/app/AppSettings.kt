package org.cyphr.app

import android.content.Context
import org.cyphr.app.crypto.EncryptedStore
import org.json.JSONObject
import java.io.File

object AppSettings {

    private const val FILE_NAME = "settings.json"

    fun isBiometricUnlockEnabled(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("biometricUnlock", false)
        } catch (_: Exception) {
            false
        }
    }

    fun setBiometricUnlockEnabled(context: Context, enabled: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        json.put("biometricUnlock", enabled)
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("onboardingCompleted", false)
        } catch (_: Exception) {
            false
        }
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        json.put("onboardingCompleted", completed)
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    fun getSelectedContactUuid(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val uuid = json.optString("selectedContactUuid", "")
            uuid.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun setSelectedContactUuid(context: Context, uuid: String?, name: String?) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        if (uuid != null) json.put("selectedContactUuid", uuid)
        else json.remove("selectedContactUuid")
        if (name != null) json.put("selectedContactName", name)
        else json.remove("selectedContactName")
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    fun getSelectedContactName(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val name = json.optString("selectedContactName", "")
            name.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun isEncryptionTileActive(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("encryptionTileActive", false)
        } catch (_: Exception) {
            false
        }
    }

    fun setEncryptionTileActive(context: Context, active: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        json.put("encryptionTileActive", active)
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    fun isEncryptModeEnabled(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text == null || try {
            JSONObject(text).optBoolean("encryptModeEnabled", true)
        } catch (_: Exception) {
            true
        }
    }

    fun setEncryptModeEnabled(context: Context, enabled: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        json.put("encryptModeEnabled", enabled)
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    fun getKeyboardLanguageCode(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val code = json.optString("keyboardLanguageCode", "")
            code.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun setKeyboardLanguageCode(context: Context, code: String?) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        if (code != null) json.put("keyboardLanguageCode", code)
        else json.remove("keyboardLanguageCode")
        EncryptedStore.writeText(context, settingsFile(context), json.toString())
    }

    private fun settingsFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}

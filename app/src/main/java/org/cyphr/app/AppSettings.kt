package org.cyphr.app

import android.content.Context
import android.util.Log
import org.cyphr.app.crypto.EncryptedStore
import org.cyphr.app.crypto.EncryptedStoreException
import org.json.JSONObject
import java.io.File

object AppSettings {

    private const val FILE_NAME = "settings.json"

    fun isBiometricUnlockEnabled(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("biometricUnlock", false)
        } catch (_: Exception) {
            Log.w("AppSettings", "optBoolean(biometricUnlock) failed")
            false
        }
    }

    fun setBiometricUnlockEnabled(context: Context, enabled: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        json.put("biometricUnlock", enabled)
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setBiometricUnlockEnabled failed: ${e.message}") }
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("onboardingCompleted", false)
        } catch (_: Exception) {
            Log.w("AppSettings", "optBoolean(onboardingCompleted) failed")
            false
        }
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        json.put("onboardingCompleted", completed)
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setOnboardingCompleted failed: ${e.message}") }
    }

    fun getSelectedContactUuid(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val uuid = json.optString("selectedContactUuid", "")
            uuid.ifBlank { null }
        } catch (_: Exception) {
            Log.w("AppSettings", "optString(selectedContactUuid) failed")
            null
        }
    }

    fun setSelectedContactUuid(context: Context, uuid: String?, name: String?) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        if (uuid != null) json.put("selectedContactUuid", uuid)
        else json.remove("selectedContactUuid")
        if (name != null) json.put("selectedContactName", name)
        else json.remove("selectedContactName")
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setSelectedContactUuid failed: ${e.message}") }
    }

    fun getSelectedContactName(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val name = json.optString("selectedContactName", "")
            name.ifBlank { null }
        } catch (_: Exception) {
            Log.w("AppSettings", "optString(selectedContactName) failed")
            null
        }
    }

    fun isEncryptionTileActive(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text != null && try {
            JSONObject(text).optBoolean("encryptionTileActive", false)
        } catch (_: Exception) {
            Log.w("AppSettings", "optBoolean(encryptionTileActive) failed")
            false
        }
    }

    fun setEncryptionTileActive(context: Context, active: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        json.put("encryptionTileActive", active)
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setEncryptionTileActive failed: ${e.message}") }
    }

    fun isEncryptModeEnabled(context: Context): Boolean {
        val text = EncryptedStore.readText(context, settingsFile(context))
        return text == null || try {
            JSONObject(text).optBoolean("encryptModeEnabled", true)
        } catch (_: Exception) {
            Log.w("AppSettings", "optBoolean(encryptModeEnabled) failed")
            true
        }
    }

    fun setEncryptModeEnabled(context: Context, enabled: Boolean) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        json.put("encryptModeEnabled", enabled)
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setEncryptModeEnabled failed: ${e.message}") }
    }

    fun getKeyboardLanguageCode(context: Context): String? {
        val text = EncryptedStore.readText(context, settingsFile(context)) ?: return null
        return try {
            val json = JSONObject(text)
            val code = json.optString("keyboardLanguageCode", "")
            code.ifBlank { null }
        } catch (_: Exception) {
            Log.w("AppSettings", "optString(keyboardLanguageCode) failed")
            null
        }
    }

    fun setKeyboardLanguageCode(context: Context, code: String?) {
        val existing = EncryptedStore.readText(context, settingsFile(context))
        val json = if (existing != null) {
            try { JSONObject(existing) } catch (_: Exception) { Log.w("AppSettings", "JSON parse failed"); JSONObject() }
        } else {
            JSONObject()
        }
        if (code != null) json.put("keyboardLanguageCode", code)
        else json.remove("keyboardLanguageCode")
        try { EncryptedStore.writeText(context, settingsFile(context), json.toString()) }
        catch (e: EncryptedStoreException) { Log.w("AppSettings", "setKeyboardLanguageCode failed: ${e.message}") }
    }

    private fun settingsFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}

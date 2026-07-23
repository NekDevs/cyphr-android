package org.cyphr.app.crypto

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

class EncryptedStoreException(message: String, cause: Throwable?) : Exception(message, cause)

object EncryptedStore {

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    @Throws(EncryptedStoreException::class)
    fun writeBytes(context: Context, file: File, data: ByteArray) {
        file.parentFile?.mkdirs()
        try {
            file.delete()
            val encryptedFile = EncryptedFile.Builder(
                context, file, masterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { it.write(data) }
        } catch (e: Exception) {
            throw EncryptedStoreException(
                "Failed to write encrypted file ${file.name}: ${e.message}", e
            )
        }
    }

    fun readBytes(context: Context, file: File): ByteArray? {
        return try {
            if (file.exists()) {
                val encryptedFile = EncryptedFile.Builder(
                    context, file, masterKey(context),
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encryptedFile.openFileInput().use { it.readBytes() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("CyphrStore", "readBytes failed for ${file.name}: ${e.message}")
            null
        }
    }

    @Throws(EncryptedStoreException::class)
    fun writeText(context: Context, file: File, text: String) {
        writeBytes(context, file, text.toByteArray())
    }

    fun readText(context: Context, file: File): String? {
        return readBytes(context, file)?.decodeToString()
    }
}

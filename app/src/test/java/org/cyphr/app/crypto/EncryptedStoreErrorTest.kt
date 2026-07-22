package org.cyphr.app.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EncryptedStoreErrorTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(context.filesDir, "encrypted-store-test")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
        tempDir.deleteRecursively()
    }

    @Test
    fun readBytesReturnsNullWhenFileDoesNotExist() {
        val result = EncryptedStore.readBytes(context, File(tempDir, "nonexistent.bin"))
        assertNull(result)
    }

    @Test
    fun readTextReturnsNullWhenFileDoesNotExist() {
        val result = EncryptedStore.readText(context, File(tempDir, "nonexistent.txt"))
        assertNull(result)
    }

    @Test
    fun writeAndReadBytesRoundTrip() {
        val file = File(tempDir, "roundtrip.bin")
        val data = "hello encrypted store".toByteArray()
        EncryptedStore.writeBytes(context, file, data)
        val result = EncryptedStore.readBytes(context, file)
        assertArrayEquals(data, result)
    }

    @Test
    fun writeAndReadEmptyBytes() {
        val file = File(tempDir, "empty.bin")
        EncryptedStore.writeBytes(context, file, byteArrayOf())
        val result = EncryptedStore.readBytes(context, file)
        assertArrayEquals(byteArrayOf(), result)
    }

    @Test
    fun writeAndReadTextRoundTrip() {
        val file = File(tempDir, "text-roundtrip.txt")
        val text = "  some text with spaces  "
        EncryptedStore.writeText(context, file, text)
        val result = EncryptedStore.readText(context, file)
        assertEquals(text, result)
    }

    @Test
    fun overwriteExistingFileProducesNewContent() {
        val file = File(tempDir, "overwrite.txt")
        EncryptedStore.writeText(context, file, "first content")
        EncryptedStore.writeText(context, file, "second content")
        val result = EncryptedStore.readText(context, file)
        assertEquals("second content", result)
    }

    @Test
    fun readCorruptedFileReturnsNull() {
        val file = File(tempDir, "corrupted.bin")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })
        val result = EncryptedStore.readBytes(context, file)
        assertNull(result)
    }

    @Test
    fun multipleFilesAreIndependent() {
        val fileA = File(tempDir, "a.txt")
        val fileB = File(tempDir, "b.txt")
        EncryptedStore.writeText(context, fileA, "content a")
        EncryptedStore.writeText(context, fileB, "content b")
        assertEquals("content a", EncryptedStore.readText(context, fileA))
        assertEquals("content b", EncryptedStore.readText(context, fileB))
    }

    @Test
    fun writeBytesWithEmptyTextFile() {
        val file = File(tempDir, "empty-text.txt")
        EncryptedStore.writeText(context, file, "")
        val result = EncryptedStore.readText(context, file)
        assertEquals("", result)
    }

    @Test
    fun readBytesOnDirectoryReturnsNull() {
        val dir = File(tempDir, "subdir")
        dir.mkdirs()
        assertNull(EncryptedStore.readBytes(context, dir))
    }
}

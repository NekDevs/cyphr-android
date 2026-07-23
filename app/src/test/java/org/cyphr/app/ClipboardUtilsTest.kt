package org.cyphr.app

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class ClipboardUtilsTest {

    @Test
    fun markSensitiveIfSupportedReturnsSameClipData() {
        val label = "test"
        val text = "sensitive content"
        val clip = ClipData.newPlainText(label, text)
        val result = clip.markSensitiveIfSupported()
        assertSame(clip, result)
    }

    @Test
    fun markSensitiveIfSupportedSetsSensitiveFlag() {
        val clip = ClipData.newPlainText("test", "secret")
        clip.markSensitiveIfSupported()
        val extras = clip.description.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }
}

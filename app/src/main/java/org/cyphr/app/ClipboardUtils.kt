package org.cyphr.app

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/**
 * Marks the clip as sensitive so that clipboard history/sync apps on
 * Android 13+ (API 33+) do not retain it.
 *
 * This is a best-effort mitigation; older Android versions and third-party
 * clipboard managers may still retain the contents.
 */
fun ClipData.markSensitiveIfSupported(): ClipData {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        description.extras = extras
    }
    return this
}

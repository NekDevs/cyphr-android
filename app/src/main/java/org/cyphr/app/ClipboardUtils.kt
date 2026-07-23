package org.cyphr.app

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

fun ClipData.markSensitiveIfSupported(): ClipData {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        description.extras = extras
    }
    return this
}

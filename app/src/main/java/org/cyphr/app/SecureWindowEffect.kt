package org.cyphr.app

import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Adds [WindowManager.LayoutParams.FLAG_SECURE] to the current window.
 *
 * Use this inside dialog or bottom-sheet composables that display sensitive
 * material (keys, decrypted text, fingerprints) so screenshots and screen
 * recordings are blocked for that window.
 */
@Composable
fun SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findWindow()
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            // Do not clear the flag; other content may still be sensitive.
        }
    }
}

private fun Context.findWindow(): Window? {
    var current: Context? = this
    while (current != null) {
        if (current is android.app.Activity) {
            return current.window
        }
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

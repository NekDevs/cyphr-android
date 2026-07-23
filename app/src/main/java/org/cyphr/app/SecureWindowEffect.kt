package org.cyphr.app

import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findWindow()
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {}
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

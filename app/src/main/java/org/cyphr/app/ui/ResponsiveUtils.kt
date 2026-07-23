package org.cyphr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val RESPONSIVE_MAX_WIDTH = 600.dp

@Composable
fun MaxWidthBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = RESPONSIVE_MAX_WIDTH,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth)) {
            content()
        }
    }
}

package org.cyphr.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePublicKeySheet(
    blob: String,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SecureWindowEffect()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            SheetOption(
                text = stringResource(R.string.share_sheet_qr),
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onShowQrCode()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SheetOption(
                text = stringResource(R.string.share_sheet_copy),
                onClick = {
                    copyToClipboard(context, blob)
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SheetOption(
                text = stringResource(R.string.share_sheet_share_via),
                onClick = {
                    shareViaSystem(context, blob)
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            )
        }
    }
}

@Composable
private fun SheetOption(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun copyToClipboard(context: Context, blob: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.share_label), blob).markSensitiveIfSupported()
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(clip)
}

private fun shareViaSystem(context: Context, blob: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, blob)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_intent_title)))
    } catch (_: Exception) { Log.w("CyphrShareKey", "share intent failed") }
}

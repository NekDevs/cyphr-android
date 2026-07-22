package org.cyphr.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.crypto.EncryptedStoreException
import org.cyphr.app.crypto.MessageLogStore
import org.cyphr.app.crypto.StoredMessage
import org.cyphr.app.ui.theme.CyphrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageLogScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var messages by remember { mutableStateOf<List<StoredMessage>>(emptyList()) }
    var selectedMessage by remember { mutableStateOf<StoredMessage?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    suspend fun loadMessages() {
        messages = withContext(Dispatchers.IO) {
            MessageLogStore.loadMessages(context, CryptoState.activeProfileUuid)
        }
    }

    LaunchedEffect(refreshTrigger) {
        loadMessages()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.log_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.log_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.log_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    MessageCard(
                        message = msg,
                        onClick = { selectedMessage = msg },
                        onDelete = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        MessageLogStore.deleteMessage(context, msg.profileUuid, msg.messageId)
                                    }
                                    loadMessages()
                                } catch (e: EncryptedStoreException) {
                                    snackbarHostState.showSnackbar(context.getString(R.string.log_delete_error, e.message))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    val sm = selectedMessage
    if (sm != null) {
        MessageDetailDialog(
            message = sm,
            onDismiss = { selectedMessage = null }
        )
    }
}

@Composable
private fun MessageCard(
    message: StoredMessage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (message.isOutgoing) context.getString(R.string.log_to_prefix, message.senderDisplayName ?: context.getString(R.string.log_unknown))
                           else (message.senderDisplayName ?: context.getString(R.string.log_unknown_sender)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = message.decryptedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.messageText.take(80).replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.log_delete_button), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MessageDetailDialog(
    message: StoredMessage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (message.isOutgoing) context.getString(R.string.log_to_prefix, message.senderDisplayName ?: context.getString(R.string.log_unknown))
                else (message.senderDisplayName ?: context.getString(R.string.log_unknown_sender))
            )
        },
        text = {
            Column {
                if (message.senderFingerprint != null) {
                    Text(
                        text = context.getString(R.string.log_fingerprint, message.senderFingerprint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = context.getString(R.string.log_counter, message.replayCounter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = context.getString(R.string.log_key_epoch, message.keyEpoch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (message.isOutgoing) context.getString(R.string.log_sent_prefix, message.decryptedAt)
                           else context.getString(R.string.log_decrypted_prefix, message.decryptedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.log_close))
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MessageLogScreenPreview() {
    CyphrTheme { MessageLogScreen(onNavigateBack = {}) }
}

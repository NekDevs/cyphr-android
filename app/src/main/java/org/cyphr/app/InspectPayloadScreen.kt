package org.cyphr.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.crypto.ContactKeyStore
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.DecodedPayload
import org.cyphr.app.crypto.ExchangeBlob
import org.cyphr.app.crypto.MessageLogStore
import org.cyphr.app.crypto.PAYLOAD_BEGIN_DELIMITER
import org.cyphr.app.crypto.PAYLOAD_END_DELIMITER
import org.cyphr.app.crypto.PayloadDecoder
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ReplayProtectionStore
import org.cyphr.app.crypto.SenderResolution
import org.cyphr.app.crypto.StoredMessage
import org.cyphr.app.ui.MaxWidthBox
import org.cyphr.app.ui.theme.CyphrTheme
import java.util.Date
import java.util.UUID

private val ISO_8601 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
    timeZone = java.util.TimeZone.getTimeZone("UTC")
}

private data class InspectResult(
    val decoded: DecodedPayload?,
    val decryptingProfileUuid: String?,
    val matchedProfileName: String?,
    val senderName: String?,
    val senderFingerprint: String?,
    val activeKeyset: ByteArray?,
    val replayRejected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectPayloadScreen(onNavigateBack: () -> Unit) {
    var payloadInput by rememberSaveable { mutableStateOf("") }
    var contactNameInput by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var inspectResult by remember { mutableStateOf<String?>(null) }
    var lastDecoded by remember { mutableStateOf<DecodedPayload?>(null) }
    var lastProfileUuid by remember { mutableStateOf<String?>(null) }
    var lastProfileName by remember { mutableStateOf<String?>(null) }
    var lastSenderName by remember { mutableStateOf<String?>(null) }
    var lastSenderFingerprint by remember { mutableStateOf<String?>(null) }
    var showSaveContactDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var autoClearJob by remember { mutableStateOf<Job?>(null) }

    @Suppress("LocalContextGetResourceValueCall")
    suspend fun performInspect() {
        if (payloadInput.isBlank()) {
            inspectResult = null
            lastDecoded = null
            return
        }

        val activeKeyset = CryptoState.profileKeysetBytes
        val result = withContext(Dispatchers.IO) {
            var decoded: DecodedPayload? = null
            var decryptingProfileUuid: String? = null
            var matchedProfileName: String? = null

            if (activeKeyset != null) {
                decoded = PayloadDecoder.decodePayload(
                    encodedPayload = payloadInput,
                    recipientKeysetBytes = activeKeyset
                )
                if (decoded != null) {
                    decryptingProfileUuid = CryptoState.activeProfileUuid
                }

                if (decoded == null) {
                    val prevKeyset = ProfileKeyManager.loadPreviousProfileKeys(
                        context, CryptoState.activeProfileUuid
                    )
                    if (prevKeyset != null) {
                        decoded = PayloadDecoder.decodePayload(
                            encodedPayload = payloadInput,
                            recipientKeysetBytes = prevKeyset
                        )
                        if (decoded != null) {
                            decryptingProfileUuid = CryptoState.activeProfileUuid
                        }
                    }
                }
            }

            if (decoded == null) {
                val allUuids = ProfileKeyManager.listAllProfileUuids(context)
                val activeUuid = CryptoState.activeProfileUuid
                for (uuid in allUuids) {
                    if (uuid == activeUuid) continue

                    val keys = ProfileKeyManager.loadProfileKeys(context, uuid)
                    if (keys != null) {
                        val res = PayloadDecoder.decodePayload(
                            encodedPayload = payloadInput,
                            recipientKeysetBytes = keys
                        )
                        if (res != null) {
                            decoded = res
                            decryptingProfileUuid = uuid
                            val meta = ProfileKeyManager.loadProfileMetadata(context, uuid)
                            matchedProfileName = meta?.optString("displayName", uuid) ?: uuid
                            break
                        }
                    }

                    val prevKeys = ProfileKeyManager.loadPreviousProfileKeys(context, uuid)
                    if (prevKeys != null) {
                        val res = PayloadDecoder.decodePayload(
                            encodedPayload = payloadInput,
                            recipientKeysetBytes = prevKeys
                        )
                        if (res != null) {
                            decoded = res
                            decryptingProfileUuid = uuid
                            val meta = ProfileKeyManager.loadProfileMetadata(context, uuid)
                            matchedProfileName = meta?.optString("displayName", uuid) ?: uuid
                            break
                        }
                    }
                }
            }

            val senderName: String?
            val senderFingerprint: String?
            var replayRejected = false
            if (decryptingProfileUuid != null && decoded != null) {
                val res = ContactKeyStore.resolveSender(
                    context, decryptingProfileUuid, decoded.senderPublicKeyBytes
                )
                senderName = when (res) {
                    is SenderResolution.KnownContact -> {
                        val store = ReplayProtectionStore(context)
                        val accepted = store.acceptReceivedCounter(
                            decryptingProfileUuid, res.contactUuid, decoded.replayCounter
                        )
                        if (!accepted) {
                            replayRejected = true
                        }
                        res.displayName
                    }
                    is SenderResolution.UnknownKey -> null
                    SenderResolution.NoSenderInfo -> null
                }
                senderFingerprint = if (decoded.senderPublicKeyBytes != null) {
                    ExchangeBlob.shortFingerprint(decoded.senderPublicKeyBytes)
                } else null
            } else {
                senderName = null
                senderFingerprint = null
            }

            InspectResult(
                decoded = if (replayRejected) null else decoded,
                decryptingProfileUuid = if (replayRejected) null else decryptingProfileUuid,
                matchedProfileName = if (replayRejected) null else matchedProfileName,
                senderName = if (replayRejected) null else senderName,
                senderFingerprint = if (replayRejected) null else senderFingerprint,
                activeKeyset = activeKeyset,
                replayRejected = replayRejected
            )
        }

        val decoded = result.decoded
        lastDecoded = decoded
        lastProfileUuid = result.decryptingProfileUuid
        lastProfileName = result.matchedProfileName
        lastSenderName = result.senderName
        lastSenderFingerprint = result.senderFingerprint

        inspectResult = if (result.replayRejected) {
            context.getString(R.string.inspect_replay_detected)
        } else if (decoded != null) {
            val profileLine = if (result.matchedProfileName != null) {
                "${context.getString(R.string.inspect_decrypted_with, result.matchedProfileName)}\n"
            } else {
                ""
            }
            val senderBlock = if (result.decryptingProfileUuid != null) {
                val res = withContext(Dispatchers.IO) {
                    ContactKeyStore.resolveSender(
                        context, result.decryptingProfileUuid, decoded.senderPublicKeyBytes
                    )
                }
                when (res) {
                    is SenderResolution.KnownContact -> {
                        val qualifier = when {
                            res.keyChanged -> context.getString(R.string.inspect_unverified_key_changed)
                            res.status != "verified" -> context.getString(R.string.inspect_unverified)
                            else -> ""
                        }
                        context.getString(R.string.inspect_from_prefix, res.displayName, qualifier)
                    }
                    is SenderResolution.UnknownKey ->
                        "${context.getString(R.string.inspect_from_unknown, res.shortFingerprint)}\n"
                    SenderResolution.NoSenderInfo -> ""
                }
            } else ""
            "${profileLine}${senderBlock}${context.getString(R.string.inspect_counter_prefix, decoded.replayCounter)}${context.getString(R.string.inspect_message_prefix, decoded.messageText.decodeToString())}"
        } else if (result.activeKeyset == null) {
            context.getString(R.string.inspect_no_keys)
        } else {
            context.getString(R.string.inspect_decryption_failed)
        }
    }

    LaunchedEffect(Unit) {
        val pending = PendingPayload.payload
        if (pending != null) {
            payloadInput = pending
            PendingPayload.payload = null
            performInspect()
        }
    }

    if (showSaveContactDialog) {
        lastDecoded?.let { decoded ->
            val fp = lastSenderFingerprint
            val defaultName = if (fp != null) stringResource(R.string.contacts_name_template, fp) else ""
            val myBlobForClipboard = remember { CryptoState.getMyExchangeBlob() }
            AlertDialog(
            onDismissRequest = { showSaveContactDialog = false },
            title = { Text(stringResource(R.string.inspect_save_sender_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.inspect_sender_fingerprint, lastSenderFingerprint ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = contactNameInput,
                        onValueChange = { contactNameInput = it },
                        label = { Text(stringResource(R.string.contacts_display_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = contactNameInput.ifBlank { defaultName }
                    val contactUuid = UUID.randomUUID().toString()
                    val senderKey = decoded.senderPublicKeyBytes ?: return@TextButton
                    try {
                        ContactKeyStore.saveContactWithFullMetadata(
                            context = context,
                            profileUuid = lastProfileUuid ?: CryptoState.activeProfileUuid,
                            contactUuid = contactUuid,
                            displayName = name,
                            publicKeyBytes = senderKey,
                            algorithmTag = org.cyphr.app.crypto.ExchangeBlob.ALGORITHM_TAG,
                            keyEpoch = decoded.keyEpoch,
                            fingerprint = org.cyphr.app.crypto.ExchangeBlob.fingerprint(senderKey),
                            verificationStatus = "unverified"
                        )
                        myBlobForClipboard?.let { myBlob ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(
                                @Suppress("LocalContextGetResourceValueCall")
                            ClipData.newPlainText(context.getString(R.string.share_label), myBlob).markSensitiveIfSupported()
                            )
                        }
                        showSaveContactDialog = false
                        contactNameInput = ""
                        @Suppress("LocalContextGetResourceValueCall")
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.contacts_saved)) }
                    } catch (e: Exception) {
                        @Suppress("LocalContextGetResourceValueCall")
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.contacts_save_error, e.message)) }
                    }
                }) {
                    Text(stringResource(R.string.inspect_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveContactDialog = false
                    contactNameInput = ""
                }) {
                    Text(stringResource(R.string.inspect_back))
                }
            }
        )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inspect_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.inspect_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        MaxWidthBox(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp)
            ) {
            OutlinedTextField(
                value = payloadInput,
                onValueChange = { payloadInput = it },
                label = { Text(stringResource(R.string.inspect_input_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                singleLine = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    @Suppress("LocalContextGetResourceValueCall")
                    scope.launch {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipboard?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString()?.trim()
                                if (text != null && text.startsWith(PAYLOAD_BEGIN_DELIMITER) && text.endsWith(PAYLOAD_END_DELIMITER)) {
                                    payloadInput = text
                                    performInspect()
                                    snackbarHostState.showSnackbar(context.getString(R.string.inspect_paste_success))
                                } else {
                                    snackbarHostState.showSnackbar(context.getString(R.string.inspect_no_payload))
                                }
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.inspect_clipboard_empty))
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(context.getString(R.string.inspect_clipboard_error))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.inspect_paste_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        delay(1)
                        performInspect()
                        isLoading = false
                    }
                },
                enabled = CryptoFeatureFlag.isEnabled && payloadInput.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.inspect_button))
            }

            if (inspectResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.inspect_decoded_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(
                                onClick = {
                                    val messageText = lastDecoded?.messageText?.decodeToString() ?: inspectResult
                                    @Suppress("LocalContextGetResourceValueCall")
                                    val clip = ClipData.newPlainText(context.getString(R.string.clipboard_cyphr_decoded), messageText)
                                        .markSensitiveIfSupported()
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(clip)
                                    autoClearJob?.cancel()
                                    autoClearJob = scope.launch {
                                        @Suppress("LocalContextGetResourceValueCall")
                                        snackbarHostState.showSnackbar(context.getString(R.string.inspect_copied))
                                        delay(30_000L)
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.clearPrimaryClip()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.inspect_copy_button))
                            }
                            val decoded = lastDecoded
                            if (decoded != null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val profileUuid = lastProfileUuid ?: CryptoState.activeProfileUuid ?: return@launch
                                                val epochMeta = withContext(Dispatchers.IO) {
                                                    ProfileKeyManager.loadProfileMetadata(context, profileUuid)
                                                }
                                                val epoch = epochMeta?.optInt("keyEpoch", 1) ?: 1
                                                val msg = StoredMessage(
                                                    messageId = UUID.randomUUID().toString(),
                                                    profileUuid = profileUuid,
                                                    senderContactUuid = null,
                                                    senderDisplayName = lastSenderName,
                                                    senderFingerprint = lastSenderFingerprint,
                                                    replayCounter = decoded.replayCounter,
                                                    messageText = decoded.messageText.decodeToString(),
                                                    rawPayload = payloadInput,
                                                    decryptedAt = ISO_8601.format(Date()),
                                                    keyEpoch = epoch
                                                )
                                                withContext(Dispatchers.IO) {
                                                    MessageLogStore.saveMessage(context, msg)
                                                }
                                                @Suppress("LocalContextGetResourceValueCall")
                                                snackbarHostState.showSnackbar(context.getString(R.string.inspect_message_saved))
                                            } catch (e: Exception) {
                                                @Suppress("LocalContextGetResourceValueCall")
                                                snackbarHostState.showSnackbar(context.getString(R.string.transform_save_error, e.message))
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.inspect_save))
                                }
                                if (decoded.senderPublicKeyBytes != null && lastSenderName == null) {
                                    TextButton(
                                        onClick = {
                                            contactNameInput = ""
                                            showSaveContactDialog = true
                                        }
                                    ) {
                                        Text(stringResource(R.string.inspect_save_as_contact))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val ir = inspectResult
                        if (ir != null) {
                            Text(
                                text = ir,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.inspect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InspectPayloadScreenPreview() {
    CyphrTheme {
        InspectPayloadScreen(onNavigateBack = {})
    }
}

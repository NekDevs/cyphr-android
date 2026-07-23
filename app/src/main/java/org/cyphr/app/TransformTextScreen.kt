package org.cyphr.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.crypto.ContactKeyMeta
import org.cyphr.app.crypto.ContactKeyStore
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.MessageLogStore
import org.cyphr.app.crypto.PayloadEncoder
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ReplayProtectionStore
import org.cyphr.app.crypto.StoredMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.cyphr.app.crypto.ProfileKeyManager.ProfileInfo
import org.cyphr.app.ui.MaxWidthBox
import org.cyphr.app.ui.theme.CyphrTheme

private val ISO_8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformTextScreen(onNavigateBack: () -> Unit) {
    var textInput by rememberSaveable { mutableStateOf("") }
    var transformResult by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactKeyMeta>>(emptyList()) }
    var selectedContact by remember { mutableStateOf<ContactKeyMeta?>(null) }
    var senderEpoch by remember { mutableIntStateOf(1) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var activeProfile by remember { mutableStateOf<ProfileInfo?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(refreshTrigger) {
        val loaded = withContext(Dispatchers.IO) {
            ContactKeyStore.listContactKeys(context, CryptoState.activeProfileUuid)
        }
        contacts = loaded
        if (selectedContact == null && loaded.isNotEmpty()) {
            selectedContact = loaded.first()
        }
        val meta = withContext(Dispatchers.IO) {
            ProfileKeyManager.loadProfileMetadata(context, CryptoState.activeProfileUuid)
        }
        senderEpoch = meta?.optInt("keyEpoch", 1) ?: 1
        activeProfile = withContext(Dispatchers.IO) {
            CryptoState.listProfiles().find { it.uuid == CryptoState.activeProfileUuid }
        }
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
                title = { Text(stringResource(R.string.transform_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.transform_back))
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
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text(stringResource(R.string.transform_input_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                singleLine = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            val byteSize = remember(textInput) { textInput.toByteArray().size }
            if (byteSize > PayloadEncoder.PAYLOAD_SIZE_WARN_BYTES) {
                Text(
                    text = stringResource(R.string.transform_size_warning, byteSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val profile = activeProfile
            if (profile != null) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.transform_profile_label, profile.displayName),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (profile.uuid == "default") stringResource(R.string.transform_profile_default) else stringResource(R.string.transform_profile_custom),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.transform_key_epoch, profile.keyEpoch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.transform_recipient),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        val sc = selectedContact
                        if (sc != null) {
                            val statusColor = if (sc.status == "verified")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                text = if (sc.status == "verified") stringResource(R.string.contacts_status_verified) else stringResource(R.string.contacts_status_unverified),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (contacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.transform_no_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        contacts.forEach { contact ->
                            val isSelected = selectedContact?.contactUuid == contact.contactUuid
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .clickable { selectedContact = contact },
                                colors = if (isSelected) CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else CardDefaults.outlinedCardColors()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = contact.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (contact.keyChanged) {
                                                Spacer(modifier = Modifier.padding(start = 6.dp))
                                                    Text(
                                                        text = stringResource(R.string.transform_key_changed),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Text(
                                            text = contact.shortFingerprint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (contact.status == "verified") {
                                        Text(
                                            text = stringResource(R.string.contacts_status_verified),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        isLoading = true
                        transformResult = try {
                                withContext(Dispatchers.IO) {
                                    val recipientUuid = selectedContact?.contactUuid
                                    val recipientKey = if (recipientUuid != null) {
                                        ContactKeyStore.loadContactPublicKey(
                                            context, CryptoState.activeProfileUuid, recipientUuid
                                        )
                                    } else null

                                     if (recipientKey != null && recipientUuid != null) {
                                         val store = ReplayProtectionStore(context)
                                         val replayCounter = store.nextSendCounter(CryptoState.activeProfileUuid, recipientUuid)

                                        val result = PayloadEncoder.encodePayload(
                                            plaintextMessage = textInput.toByteArray(),
                                            senderKeyEpoch = senderEpoch,
                                            recipientPublicKeyBytes = recipientKey,
                                            senderPublicKeyBytes = CryptoState.profilePublicKeyBytes,
                                            replayCounter = replayCounter
                                        )

                                        if (result != null) {
                                            val contactMeta = ContactKeyStore.getContact(
                                                context, CryptoState.activeProfileUuid, recipientUuid
                                            )
                                            val sentMsg = StoredMessage(
                                                messageId = UUID.randomUUID().toString(),
                                                profileUuid = CryptoState.activeProfileUuid,
                                                senderContactUuid = recipientUuid,
                                                senderDisplayName = contactMeta?.displayName,
                                                senderFingerprint = contactMeta?.shortFingerprint,
                                                replayCounter = replayCounter,
                                                messageText = textInput,
                                                rawPayload = result,
                                                decryptedAt = ISO_8601.format(Date()),
                                                keyEpoch = senderEpoch,
                                                isOutgoing = true
                                            )
                                            MessageLogStore.saveMessage(context, sentMsg)
                                        }
                                        result ?: "Encryption failed"
                                    } else {
                                        @Suppress("LocalContextGetResourceValueCall")
                                        if (contacts.isEmpty()) {
                                            context.getString(R.string.transform_no_contacts_available)
                                        } else {
                                            context.getString(R.string.transform_no_key)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                @Suppress("LocalContextGetResourceValueCall")
                                context.getString(R.string.transform_storage_error, e.message)
                            }
                        isLoading = false
                    }
                },
                enabled = CryptoFeatureFlag.isEnabled && textInput.isNotBlank() && selectedContact != null && !isLoading,
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
                Text(stringResource(R.string.transform_button))
            }

            val result = transformResult
            if (result != null) {
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
                                text = stringResource(R.string.transform_result_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(
                                        ClipData.newPlainText("Cyphr payload", result).markSensitiveIfSupported()
                                    )
                                    @Suppress("LocalContextGetResourceValueCall")
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.transform_copied)) }
                                }
                            ) {
                                Text(stringResource(R.string.transform_copy_button))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.transform_hint),
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
fun TransformTextScreenPreview() {
    CyphrTheme {
        TransformTextScreen(onNavigateBack = {})
    }
}
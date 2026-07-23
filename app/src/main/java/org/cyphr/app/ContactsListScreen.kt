package org.cyphr.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import org.cyphr.app.crypto.ExchangeBlob
import org.cyphr.app.ui.MaxWidthBox
import org.cyphr.app.ui.theme.CyphrTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListScreen(
    onNavigateBack: () -> Unit,
    onContactTap: (String) -> Unit,
    onNavigateToScanQr: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactKeyMeta>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    var blobInput by remember { mutableStateOf("") }
    var parsedBlob by remember { mutableStateOf<ExchangeBlob.ParsedBlob?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var existingContact by remember { mutableStateOf<ContactKeyMeta?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(refresh) {
        contacts = withContext(Dispatchers.IO) {
            ContactKeyStore.listContactKeys(context, CryptoState.activeProfileUuid)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(PendingPayload.payload) {
        val pending = PendingPayload.payload
        if (pending != null) {
            blobInput = pending
            PendingPayload.payload = null
        }
    }

    @Suppress("LocalContextGetResourceValueCall")
    LaunchedEffect(blobInput) {
        val trimmed = blobInput.trim()
        if (trimmed.isNotEmpty()) {
            val result = ExchangeBlob.parse(trimmed)
            if (result != null) {
                parsedBlob = result
                parseError = null
                val fp = ExchangeBlob.fingerprint(result.publicKeyBytes)
                displayName = context.getString(R.string.contacts_name_template, fp.take(4))
                val existingContactUuid = withContext(Dispatchers.IO) {
                    ContactKeyStore.findContactByFingerprint(context, CryptoState.activeProfileUuid, fp)
                }
                existingContact = if (existingContactUuid != null) {
                    withContext(Dispatchers.IO) {
                        ContactKeyStore.getContact(context, CryptoState.activeProfileUuid, existingContactUuid)
                    }
                } else null
            } else {
                parsedBlob = null
                parseError = context.getString(R.string.contacts_invalid_key)
            }
        } else {
            parsedBlob = null
            parseError = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.contacts_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        MaxWidthBox(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.contacts_add_heading),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = blobInput,
                                onValueChange = { blobInput = it },
                                label = { Text(stringResource(R.string.contacts_paste_key)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                singleLine = false
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onNavigateToScanQr,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.contacts_scan_qr))
                            }

                            val pe = parseError
                            if (pe != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            val blob = parsedBlob
                            if (blob != null) {
                                val fp = ExchangeBlob.fingerprint(blob.publicKeyBytes)
                                val short = ExchangeBlob.shortFingerprint(blob.publicKeyBytes)

                                Spacer(modifier = Modifier.height(12.dp))

                                val ec = existingContact
                                if (ec != null) {
                                    OutlinedCard(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = stringResource(R.string.contacts_already_exists),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.contacts_key_matches, ec.displayName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }

                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = short,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                val chunkedFp = remember(fp) { fp.chunked(4).joinToString(" ") }
                                Text(
                                    text = chunkedFp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.contacts_epoch_label, blob.keyEpoch),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text(stringResource(R.string.contacts_display_name_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        val contactUuid = UUID.randomUUID().toString()
                                        @Suppress("LocalContextGetResourceValueCall")
                                        val name = displayName.ifBlank { context.getString(R.string.contacts_name_template, fp.take(4)) }

                                    @Suppress("LocalContextGetResourceValueCall")
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                ContactKeyStore.saveContactWithFullMetadata(
                                                    context = context,
                                                    profileUuid = CryptoState.activeProfileUuid,
                                                    contactUuid = contactUuid,
                                                    displayName = name,
                                                    publicKeyBytes = blob.publicKeyBytes,
                                                    algorithmTag = blob.algorithmTag,
                                                    keyEpoch = blob.keyEpoch,
                                                    fingerprint = fp,
                                                    verificationStatus = "unverified"
                                                )
                                            }
                                            val myBlob = withContext(Dispatchers.IO) {
                                                CryptoState.getMyExchangeBlob()
                                            }
                                            myBlob?.let { b ->
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(
                                                    ClipData.newPlainText("Cyphr public key", b).markSensitiveIfSupported()
                                                )
                                            }
                                            blobInput = ""
                                            parsedBlob = null
                                            refresh++
                                            snackbarHostState.showSnackbar(context.getString(R.string.contacts_saved))
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.contacts_save_error, e.message))
                                        }
                                    }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.contacts_save_button))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (contacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.contacts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (contacts.isNotEmpty()) {
                    items(contacts, key = { it.contactUuid }) { contact ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { onContactTap(contact.contactUuid) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = contact.shortFingerprint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val statusLabel = when (contact.status) {
                                    "verified" -> stringResource(R.string.contacts_status_verified)
                                    "revoked" -> stringResource(R.string.contacts_status_revoked)
                                    else -> stringResource(R.string.contacts_status_unverified)
                                }
                                val statusColor = when (contact.status) {
                                    "verified" -> MaterialTheme.colorScheme.primary
                                    "revoked" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ContactsListScreenPreview() {
    CyphrTheme { ContactsListScreen(onNavigateBack = {}, onContactTap = {}, onNavigateToScanQr = {}) }
}

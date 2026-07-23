package org.cyphr.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactUuid: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<ContactKeyMeta?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUpdateField by remember { mutableStateOf(false) }
    var updateBlobInput by remember { mutableStateOf("") }
    var parsedUpdateBlob by remember { mutableStateOf<ExchangeBlob.ParsedBlob?>(null) }
    var updateParseError by remember { mutableStateOf<String?>(null) }
    var rawKey by remember { mutableStateOf<ByteArray?>(null) }
    var contactFp by remember { mutableStateOf<String?>(null) }
    var storedFp by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun loadContact() {
        val c = withContext(Dispatchers.IO) {
            ContactKeyStore.getContact(context, CryptoState.activeProfileUuid, contactUuid)
        }
        contact = c
        if (c != null) {
            val rk = withContext(Dispatchers.IO) {
                ContactKeyStore.loadContactPublicKey(context, CryptoState.activeProfileUuid, contactUuid)
            }
            rawKey = rk
            contactFp = rk?.let { ExchangeBlob.fingerprint(it) }
            val meta = withContext(Dispatchers.IO) {
                ContactKeyStore.loadContactMetadata(context, CryptoState.activeProfileUuid, contactUuid)
            }
            storedFp = meta?.optString("publicKeyFingerprint", "")
        }
    }

    LaunchedEffect(contactUuid, refreshTrigger) {
        loadContact()
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.contact_detail_delete_title)) },
            text = { Text(stringResource(R.string.contact_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    @Suppress("LocalContextGetResourceValueCall")
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ContactKeyStore.deleteContactKey(context, CryptoState.activeProfileUuid, contactUuid)
                            }
                            showDeleteConfirm = false
                            onNavigateBack()
                        } catch (e: Exception) {
                            showDeleteConfirm = false
                            snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_delete_error, e.message))
                        }
                    }
                }) {
                    Text(stringResource(R.string.contact_detail_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.contact_detail_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(contact?.displayName ?: stringResource(R.string.contact_detail_title_fallback)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.contact_detail_back))
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
                    .padding(16.dp)
            ) {
            val c = contact
            if (c == null) {
                Text(
                    text = stringResource(R.string.contact_detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val statusLabel = when (c.status) {
                "verified" -> stringResource(R.string.contacts_status_verified)
                "revoked" -> stringResource(R.string.contacts_status_revoked)
                else -> stringResource(R.string.contacts_status_unverified)
            }
            val statusColor = when (c.status) {
                "verified" -> MaterialTheme.colorScheme.primary
                "revoked" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.contact_detail_verification_status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                }
            }

            if (c.keyChanged) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.contact_detail_key_changed),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.contact_detail_key_changed_desc) +
                                    if (c.status == "verified") " " + stringResource(R.string.contact_detail_verify_first) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showUpdateField = !showUpdateField },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showUpdateField) stringResource(R.string.contact_detail_cancel_update) else stringResource(R.string.contact_detail_import_new_key))
                }

                if (showUpdateField) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = updateBlobInput,
                        onValueChange = {
                            updateBlobInput = it
                            parsedUpdateBlob = null
                            updateParseError = null
                        },
                        label = { Text(stringResource(R.string.contact_detail_paste_new_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                            onClick = {
                                val result = ExchangeBlob.parse(updateBlobInput.trim())
                                if (result != null) {
                                    parsedUpdateBlob = result
                                    updateParseError = null
                                } else {
                                    parsedUpdateBlob = null
                                    @Suppress("LocalContextGetResourceValueCall")
                                    updateParseError = context.getString(R.string.contact_detail_invalid_key)
                                }
                            },
                        enabled = updateBlobInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.contact_detail_parse))
                    }

                    val upe = updateParseError
                    if (upe != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = upe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val pub = parsedUpdateBlob
                    if (pub != null) {
                        val blob = pub
                        val newFp = remember(blob) { ExchangeBlob.fingerprint(blob.publicKeyBytes) }
                        val newShort = remember(blob) { ExchangeBlob.shortFingerprint(blob.publicKeyBytes) }

                        Spacer(modifier = Modifier.height(12.dp))

                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.contact_detail_new_fingerprint),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = newShort,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val chunkedNewFp = remember(newFp) { newFp.chunked(4).joinToString(" ") }
                                Text(
                                    text = chunkedNewFp,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                    Text(
                                        text = stringResource(R.string.contact_detail_key_epoch, blob.keyEpoch),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                @Suppress("LocalContextGetResourceValueCall")
                                scope.launch {
                                    try {
                                        val success = withContext(Dispatchers.IO) {
                                            ContactKeyStore.updateContactFromBlob(
                                                context = context,
                                                profileUuid = CryptoState.activeProfileUuid,
                                                contactUuid = contactUuid,
                                                publicKeyBytes = blob.publicKeyBytes,
                                                algorithmTag = blob.algorithmTag,
                                                keyEpoch = blob.keyEpoch
                                            )
                                        }
                                        if (success) {
                                            contact = withContext(Dispatchers.IO) {
                                                ContactKeyStore.getContact(context, CryptoState.activeProfileUuid, contactUuid)
                                            }
                                            showUpdateField = false
                                            updateBlobInput = ""
                                            parsedUpdateBlob = null
                                            snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_updated))
                                        } else {
                                            snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_update_failed))
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_update_error, e.message))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.contact_detail_update_button))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val cfp = contactFp
            if (cfp != null) {
                val activeUuid = CryptoState.activeProfileUuid
                val myFp = remember(activeUuid) { CryptoState.getMyFingerprint() }

                Text(
                    text = stringResource(R.string.contact_detail_compare_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.contact_detail_compare_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.contact_detail_their_fingerprint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val shortFp = rawKey?.let { ExchangeBlob.shortFingerprint(it) }
                        if (shortFp != null) {
                            Text(
                                text = shortFp,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        val chunkedCfp = remember(cfp) { cfp.chunked(4).joinToString(" ") }
                        Text(
                            text = chunkedCfp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (myFp != null) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.contact_detail_your_fingerprint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val myShort = remember(activeUuid) { CryptoState.getMyShortFingerprint() }
                            if (myShort != null) {
                                Text(
                                    text = myShort,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            val chunkedMyFp = remember(myFp) { myFp.chunked(4).joinToString(" ") }
                            Text(
                                text = chunkedMyFp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.contact_detail_key_epoch_label, c.keyEpoch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.contact_detail_contact_id, c.contactUuid.take(8)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (c.status != "verified") {
                Button(
                    onClick = {
                        @Suppress("LocalContextGetResourceValueCall")
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    ContactKeyStore.updateContactVerificationStatus(
                                        context, CryptoState.activeProfileUuid, contactUuid, "verified"
                                    )
                                }
                                contact = withContext(Dispatchers.IO) {
                                    ContactKeyStore.getContact(context, CryptoState.activeProfileUuid, contactUuid)
                                }
                                snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_marked_verified))
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(context.getString(R.string.contact_detail_verify_error, e.message))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.contact_detail_mark_verified))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.contact_detail_verify_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.contact_detail_delete_button))
            }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ContactDetailScreenPreview() {
    CyphrTheme { ContactDetailScreen(contactUuid = "test", onNavigateBack = {}) }
}

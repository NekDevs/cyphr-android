package org.cyphr.app.tiles

import android.content.ComponentName
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.AppSettings
import org.cyphr.app.R
import org.cyphr.app.crypto.ContactKeyMeta
import org.cyphr.app.crypto.ContactKeyStore
import org.cyphr.app.crypto.EncryptedStoreException
import org.cyphr.app.crypto.ProfileKeyManager

class ContactPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            ContactPickerContent(onContactSelected = { uuid, name ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            AppSettings.setSelectedContactUuid(this@ContactPickerActivity, uuid, name)
                        }
                        TileService.requestListeningState(
                            this@ContactPickerActivity,
                            ComponentName(this@ContactPickerActivity, QuickSettingsTileService::class.java)
                        )
                        finish()
                    } catch (e: EncryptedStoreException) {
                        Toast.makeText(this@ContactPickerActivity, getString(R.string.picker_save_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }, onDismiss = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactPickerContent(
    onContactSelected: (uuid: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactKeyMeta>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val results = withContext(Dispatchers.IO) {
            val profileUuid = ProfileKeyManager.loadActiveProfileUuid(context) ?: return@withContext emptyList()
            ContactKeyStore.listContactKeys(context, profileUuid)
        }
        contacts = results
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.picker_cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(innerPadding)
        ) {
            if (!loaded) {
                Text(
                    text = stringResource(R.string.picker_loading),
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            } else if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.picker_empty),
                    modifier = Modifier.padding(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.picker_cancel))
                }
            } else {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    AppSettings.setSelectedContactUuid(context, null, null)
                                }
                                onDismiss()
                            } catch (e: EncryptedStoreException) {
                                Toast.makeText(context, context.getString(R.string.picker_save_error, e.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.picker_none_encryption))
                }
                LazyColumn {
                    items(contacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onContactSelected(contact.contactUuid, contact.displayName)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = contact.shortFingerprint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val status = when {
                                contact.keyChanged -> stringResource(R.string.picker_key_changed)
                                contact.status == "verified" -> stringResource(R.string.picker_verified)
                                else -> stringResource(R.string.picker_unverified)
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (contact.status == "verified") Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

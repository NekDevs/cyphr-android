package org.cyphr.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.crypto.ProfileKeyManager.ProfileInfo
import org.cyphr.app.ui.MaxWidthBox
import org.cyphr.app.ui.theme.CyphrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<ProfileInfo>>(emptyList()) }
    var activeUuid by remember { mutableStateOf(CryptoState.activeProfileUuid) }
    var refresh by remember { mutableIntStateOf(0) }
    var newProfileName by remember { mutableStateOf("") }
    var showRotateConfirm by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProfileInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProfileInfo?>(null) }

    LaunchedEffect(refresh) {
        profiles = withContext(Dispatchers.IO) { CryptoState.listProfiles() }
        activeUuid = CryptoState.activeProfileUuid
    }

    val activeProfile = profiles.find { it.uuid == activeUuid }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { showRotateConfirm = false },
            title = { Text(stringResource(R.string.profile_rotate_confirm_title)) },
            text = {
                Text(stringResource(R.string.profile_rotate_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRotateConfirm = false
                    @Suppress("LocalContextGetResourceValueCall")
                    scope.launch {
                        try {
                            val success = withContext(Dispatchers.IO) {
                                CryptoState.rotateKeys(context)
                            }
                            if (success) {
                                refresh++
                                snackbarHostState.showSnackbar(context.getString(R.string.profile_rotate_success))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.profile_rotate_failed))
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(context.getString(R.string.profile_rotate_error, e.message))
                        }
                    }
                }) {
                    Text(stringResource(R.string.profile_rotate_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotateConfirm = false }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.profile_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            CryptoState.renameProfile(context, target.uuid, renameText)
                        }
                        renameTarget = null
                        refresh++
                    }
                }) {
                    Text(stringResource(R.string.profile_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = {
                val target = deleteTarget
                Text(
                    if (target != null) {
                        stringResource(R.string.profile_delete_body, target.displayName)
                    } else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget ?: return@TextButton
                    deleteTarget = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            CryptoState.deleteProfile(context, target.uuid)
                        }
                        if (deleted) refresh++
                    }
                }) {
                    Text(stringResource(R.string.profile_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.profile_back))
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
            Text(
                text = stringResource(R.string.profile_active_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (activeProfile != null) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = activeProfile.displayName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_id, activeProfile.uuid.take(8)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.profile_created, activeProfile.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.profile_key_epoch, activeProfile.keyEpoch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showRotateConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.profile_rotate_action))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = {
                                renameText = activeProfile.displayName
                                renameTarget = activeProfile
                            }) {
                                Text(stringResource(R.string.profile_rename_action))
                            }
                            TextButton(
                                onClick = { deleteTarget = activeProfile },
                                enabled = profiles.size > 1
                            ) {
                                Text(stringResource(R.string.profile_delete_action), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (profiles.size > 1) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.profile_other_heading),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                profiles.filter { it.uuid != activeUuid }.forEach { profile ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clickable {
                                scope.launch {
                                    val switched = withContext(Dispatchers.IO) {
                                        CryptoState.switchProfile(context, profile.uuid)
                                    }
                                    if (switched) refresh++
                                }
                            },
                        colors = CardDefaults.outlinedCardColors()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = profile.createdAt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.profile_switch_action),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    renameText = profile.displayName
                                    renameTarget = profile
                                }) {
                                    Text(stringResource(R.string.profile_rename_action), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { deleteTarget = profile },
                                    enabled = profiles.size > 1
                                ) {
                                    Text(stringResource(R.string.profile_delete_action),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val created = withContext(Dispatchers.IO) {
                            CryptoState.createProfile(context, displayName = newProfileName.ifBlank { null })
                        }
                        if (created != null) {
                            newProfileName = ""
                            refresh++
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.profile_create_action))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.profile_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfileScreenPreview() {
    CyphrTheme { ProfileScreen(onNavigateBack = {}) }
}

package org.cyphr.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.ui.theme.CyphrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var biometricEnabled by remember { mutableStateOf(false) }
    var biometricAvailable by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var imeEnabled by remember { mutableStateOf(false) }
    var a11yEnabled by remember { mutableStateOf(false) }

    suspend fun refreshStatus() {
        biometricEnabled = withContext(Dispatchers.IO) { AppSettings.isBiometricUnlockEnabled(context) }
        biometricAvailable = isBiometricAvailable(context)
        imeEnabled = isImeEnabled(context)
        a11yEnabled = isAccessibilityServiceEnabled(context)
        loaded = true
    }

    LaunchedEffect(refreshTrigger) {
        refreshStatus()
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_biometric_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (biometricAvailable) stringResource(R.string.settings_biometric_desc_available)
                                   else stringResource(R.string.settings_biometric_desc_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 16.dp))
                    Switch(
                        checked = biometricEnabled && biometricAvailable,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        AppSettings.setBiometricUnlockEnabled(context, enabled)
                                    }
                                    val persisted = withContext(Dispatchers.IO) {
                                        AppSettings.isBiometricUnlockEnabled(context)
                                    }
                                    biometricEnabled = persisted
                                    if (persisted != enabled) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_biometric_update_error))
                                    }
                                } catch (e: Exception) {
                                    biometricEnabled = !enabled
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_save_error, e.message))
                                }
                            }
                        },
                        enabled = biometricAvailable && loaded
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_section_keyboard),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_keyboard_name),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_keyboard_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (imeEnabled) stringResource(R.string.settings_enabled_status) else stringResource(R.string.settings_disabled_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (imeEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }) {
                            Text(stringResource(R.string.settings_enable_button))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_screen_name),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_screen_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (a11yEnabled) stringResource(R.string.settings_enabled_status) else stringResource(R.string.settings_disabled_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (a11yEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text(stringResource(R.string.settings_enable_button))
                        }
                    }
                }
            }
        }
    }
}

private fun isImeEnabled(context: android.content.Context): Boolean {
    val imeId = "org.cyphr.app/.keyboard.CyphrKeyboardService"
    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
    return imm.enabledInputMethodList.any { it.id == imeId }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceId = "org.cyphr.app/org.cyphr.app.accessibility.CyphrAccessibilityService"
    return try {
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        am.getEnabledAccessibilityServiceList(-1).any { it.id == serviceId }
    } catch (_: SecurityException) {
        false
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    CyphrTheme { SettingsScreen(onNavigateBack = {}) }
}

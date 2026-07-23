package org.cyphr.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.NamedNavArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.cyphr.app.crypto.PAYLOAD_BEGIN_DELIMITER
import org.cyphr.app.crypto.PAYLOAD_END_DELIMITER
import org.cyphr.app.ui.theme.CyphrTheme

class MainActivity : FragmentActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    private val cryptoViewModel: CryptoViewModel by viewModels()
    private var isUnlocked by mutableStateOf(false)
    private var biometricEnabled by mutableStateOf(false)
    private var wentToBackground = false
    private var foregroundCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        CryptoState.viewModel = cryptoViewModel
        cryptoViewModel.initialize(applicationContext)
        biometricEnabled = AppSettings.isBiometricUnlockEnabled(applicationContext)
        enableEdgeToEdge()
        handleSendIntent(intent)
        setContent {
            var forceDarkTheme by remember { mutableStateOf<Boolean?>(null) }
            var onboardingCompleted by remember { mutableStateOf(AppSettings.isOnboardingCompleted(applicationContext)) }
            CyphrTheme(forceDarkTheme = forceDarkTheme) {
                val initError = cryptoViewModel.initializationError
                if (initError != null) {
                    InitializationErrorScreen(
                        errorMessage = initError,
                        onRetry = {
                            cryptoViewModel.clearInitializationError()
                            cryptoViewModel.initialize(applicationContext)
                        }
                    )
                } else if (biometricEnabled && !isUnlocked) {
                    BiometricGate(onUnlocked = { isUnlocked = true })
                } else if (!onboardingCompleted) {
                    OnboardingScreen(onComplete = { onboardingCompleted = true })
                } else {
                    val navController = rememberNavController()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val bg = MaterialTheme.colorScheme.surface
                    var lastClipboardPayload by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(isUnlocked, foregroundCount) {
                        if (isUnlocked || !biometricEnabled) {
                            try {
                                val cm = this@MainActivity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@LaunchedEffect
                                val clip = cm.primaryClip ?: return@LaunchedEffect
                                if (clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString()?.trim() ?: return@LaunchedEffect
                                    if (text == lastClipboardPayload) return@LaunchedEffect
                                    if (text.startsWith(PAYLOAD_BEGIN_DELIMITER) && text.endsWith(PAYLOAD_END_DELIMITER)) {
                                        val result = snackbarHostState.showSnackbar(
                                            message = this@MainActivity.getString(R.string.main_clipboard_detected),
                                            actionLabel = this@MainActivity.getString(R.string.main_inspect_action)
                                        )
                                        lastClipboardPayload = text
                                        if (result == SnackbarResult.ActionPerformed) {
                                            PendingPayload.payload = text
                                            navController.navigate(Routes.INSPECT) {
                                                launchSingleTop = true
                                                popUpTo(Routes.LANDING) { inclusive = false }
                                            }
                                        }
                                    }
                                }
                             } catch (_: Exception) {
                                    Log.w("CyphrMain", "startActivity failed")
                                }
                        }
                    }

                    LaunchedEffect(PendingPayload.payload, PendingPayload.trigger) {
                        val currentRoute = navController.currentDestination?.route
                        if (PendingPayload.payload != null && !PendingPayload.skipInspect && currentRoute != Routes.INSPECT && currentRoute != Routes.SCAN_QR) {
                            navController.navigate(Routes.INSPECT) {
                                launchSingleTop = true
                                popUpTo(Routes.LANDING) { inclusive = false }
                            }
                        }
                        PendingPayload.skipInspect = false
                    }

                    Box(modifier = Modifier.background(bg)) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.LANDING
                        ) {
                            animatedComposable(Routes.LANDING) {
                                HomeScreen(
                                    forceDarkTheme = forceDarkTheme,
                                    onNavigateToTransformText = { navController.navigate(Routes.TRANSFORM) },
                                    onNavigateToInspectPayload = { navController.navigate(Routes.INSPECT) },
                                    onNavigateToMyIdentity = { navController.navigate(Routes.MY_IDENTITY) },
                                    onNavigateToContactList = { navController.navigate(Routes.CONTACTS) },
                                    onNavigateToProfiles = { navController.navigate(Routes.PROFILES) },
                                    onNavigateToMessageLog = { navController.navigate(Routes.MESSAGE_LOG) },
                                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                    onToggleDarkMode = {
                                        forceDarkTheme = when (forceDarkTheme) {
                                            null -> true
                                            true -> false
                                            false -> null
                                        }
                                    }
                                )
                            }
                            animatedComposable(Routes.TRANSFORM) {
                                TransformTextScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(Routes.INSPECT) {
                                InspectPayloadScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(Routes.MY_IDENTITY) {
                                MyIdentityScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToQrCode = { blob ->
                                        navController.navigate(Routes.qrCode(blob))
                                    }
                                )
                            }
                            animatedComposable(Routes.CONTACTS) {
                                ContactsListScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onContactTap = { uuid ->
                                        navController.navigate(Routes.contactDetail(uuid))
                                    },
                                    onNavigateToScanQr = {
                                        navController.navigate(Routes.SCAN_QR)
                                    }
                                )
                            }
                            animatedComposable(Routes.SCAN_QR) {
                                QrScannerScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            animatedComposable(
                                route = Routes.CONTACT_DETAIL,
                                arguments = listOf(
                                    navArgument("contactUuid") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val contactUuid = backStackEntry.arguments
                                    ?.getString("contactUuid") ?: ""
                                ContactDetailScreen(
                                    contactUuid = contactUuid,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(Routes.PROFILES) {
                                ProfileScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(Routes.MESSAGE_LOG) {
                                MessageLogScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(Routes.SETTINGS) {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            animatedComposable(
                                route = Routes.QR_CODE,
                                arguments = listOf(
                                    navArgument("blob") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val blob = Uri.decode(
                                    backStackEntry.arguments?.getString("blob") ?: ""
                                )
                                QrCodeScreen(
                                    blob = blob,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        wentToBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (wentToBackground) {
            foregroundCount++
            biometricEnabled = AppSettings.isBiometricUnlockEnabled(this)
            if (biometricEnabled) {
                isUnlocked = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            CryptoState.viewModel = null
            PendingPayload.clear()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            PendingPayload.cameraPermissionResult = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                PendingPayload.payload = sharedText
            }
        }
    }
}

@Composable
private fun InitializationErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.main_error_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.main_retry))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.main_error_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

object PendingPayload {
    var payload by mutableStateOf<String?>(null)
    var cameraPermissionResult by mutableStateOf<Boolean?>(null)
    var trigger by mutableIntStateOf(0)
    var skipInspect by mutableStateOf(false)

    fun clear() {
        payload = null
        cameraPermissionResult = null
        trigger = 0
        skipInspect = false
    }
}

private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 8 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 8 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 8 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing))
        },
        content = content
    )
}

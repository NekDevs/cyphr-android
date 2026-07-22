package org.cyphr.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedText by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(PendingPayload.cameraPermissionResult) {
        val result = PendingPayload.cameraPermissionResult
        if (result != null) {
            PendingPayload.cameraPermissionResult = null
            hasCameraPermission = result
            if (!result && activity != null) {
                val act = activity ?: return@LaunchedEffect
                permissionPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                    act, Manifest.permission.CAMERA
                )
            }
        }
    }

    LaunchedEffect(scannedText) {
        scannedText?.let {
            PendingPayload.payload = it
            PendingPayload.skipInspect = true
            PendingPayload.trigger++
            onNavigateBack()
        }
    }

    LaunchedEffect(cameraError) {
        cameraError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            delay(1500)
            onNavigateBack()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.qr_cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    onQrScanned = { text -> scannedText = text },
                    onError = { msg -> cameraError = msg }
                )

                ScanFrameOverlay(modifier = Modifier.align(Alignment.Center))

                Text(
                    text = stringResource(R.string.qr_align_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.qr_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (permissionPermanentlyDenied) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Text(stringResource(R.string.qr_open_settings))
                        }
                    } else {
                        Button(
                            onClick = {
                                activity?.requestPermissions(
                                    arrayOf(Manifest.permission.CAMERA),
                                    CAMERA_PERMISSION_REQUEST_CODE
                                )
                            },
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Text(stringResource(R.string.qr_grant_permission))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CameraPreview(
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analyzer = remember { QrCodeAnalyzer(onQrScanned) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        analyzer.isActive = true
        onDispose {
            analyzer.isActive = false
            cameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, analyzer)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                    )
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    onError(context.getString(R.string.qr_camera_error))
                } catch (_: ExecutionException) {
                    onError(context.getString(R.string.qr_camera_error))
                } catch (_: IllegalStateException) {
                    onError(context.getString(R.string.qr_camera_error))
                } catch (_: IllegalArgumentException) {
                    onError(context.getString(R.string.qr_camera_error))
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(250.dp)) {
        val w = size.width
        val h = size.height
        val cornerLen = w * 0.15f
        val sw = 4.dp.toPx()
        val c = Color.White

        drawLine(c, Offset(0f, 0f), Offset(cornerLen, 0f), sw)
        drawLine(c, Offset(0f, 0f), Offset(0f, cornerLen), sw)
        drawLine(c, Offset(w, 0f), Offset(w - cornerLen, 0f), sw)
        drawLine(c, Offset(w, 0f), Offset(w, cornerLen), sw)
        drawLine(c, Offset(0f, h), Offset(cornerLen, h), sw)
        drawLine(c, Offset(0f, h), Offset(0f, h - cornerLen), sw)
        drawLine(c, Offset(w, h), Offset(w - cornerLen, h), sw)
        drawLine(c, Offset(w, h), Offset(w, h - cornerLen), sw)
    }
}

private const val CAMERA_PERMISSION_REQUEST_CODE = 1001

private class QrCodeAnalyzer(
    private val onQrScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var isActive = true
    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
        try {
            if (!isActive) return

            val result = try {
                decodeQrFromImage(image)
            } catch (_: NotFoundException) {
                null
            } catch (_: ChecksumException) {
                null
            } catch (_: FormatException) {
                null
            }

            if (result != null) {
                isActive = false
                onQrScanned(result)
            }
        } finally {
            image.close()
        }
    }

    private fun decodeQrFromImage(image: ImageProxy): String? {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.isEmpty()) return null

        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        val ySize = buffer.remaining()
        val yData = ByteArray(ySize)
        buffer.get(yData)

        val yDataCropped = if (rowStride == width) {
            yData
        } else {
            val cropped = ByteArray(width * height)
            for (row in 0 until height) {
                val srcPos = row * rowStride
                yData.copyInto(
                    cropped, destinationOffset = row * width,
                    startIndex = srcPos, endIndex = srcPos + width
                )
            }
            cropped
        }

        val source = PlanarYUVLuminanceSource(
            yDataCropped, width, height, 0, 0, width, height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap)?.text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }
}

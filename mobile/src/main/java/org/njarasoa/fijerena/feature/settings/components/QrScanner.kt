package org.njarasoa.fijerena.feature.settings.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.njarasoa.fijerena.core.network.sync.PairingQr
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen camera that reads one live sync pairing code and hands its text to [onScanned].
 * Other QR codes are ignored, so pointing it at the wrong one just keeps it looking.
 */
@Composable
fun QrScanner(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(CinemaTheme.colors.mediaScrim)) {
        if (granted) CameraPreview(onScanned)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(CinemaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            CinemaText(
                text = stringResource(if (granted) R.string.live_sync_scan_hint else R.string.live_sync_camera_permission),
                tone = TextTone.OnMedia,
                textAlign = TextAlign.Center,
            )
            if (!granted) {
                CinemaButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    CinemaText(stringResource(R.string.live_sync_camera_grant), role = TextRole.Label, tone = TextTone.Inherit)
                }
            }
            CinemaOutlinedButton(onClick = onClose) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val delivered = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        // Both the listeners below and onDispose run on Main, so a plain flag is enough.
        var disposed = false
        val analysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                setAnalyzer(executor) { proxy ->
                    // The default YUV_420_888 frames: plane 0 is luminance, one byte per pixel.
                    val text =
                        proxy.use {
                            if (delivered.get()) return@use null
                            val plane = it.planes[0]
                            val bytes = ByteArray(plane.buffer.remaining()).also { b -> plane.buffer.get(b) }
                            decodeQrFrame(bytes, it.width, it.height, plane.rowStride).firstOrNull { qr -> PairingQr.decode(qr) != null }
                        }
                    if (text != null && delivered.compareAndSet(false, true)) {
                        mainExecutor.execute { if (!disposed) currentOnScanned(text) }
                    }
                }
            }
        providerFuture.addListener({
            // CameraX failed to start, or no back camera (some tablets): the screen stays black
            // with its hint and Cancel. Skipped when the scanner closed before the camera was ready.
            if (!disposed) {
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { android.util.Log.w("QrScanner", "Camera unavailable", it) }
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            // Never block Main on a camera that is still starting; nothing is bound until it's done.
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

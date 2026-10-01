package com.mohammed.mosa.qrscanner.scanner


import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isPaused: Boolean,
    torchEnabled: Boolean,
    useFrontCamera: Boolean = false,
    onResult: (value: String, format: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // Always-fresh values inside the analyzer callback, without rebinding.
    val currentPaused by rememberUpdatedState(isPaused)
    val currentOnResult by rememberUpdatedState(onResult)

    // Use cases created once.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val preview = remember {
        Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
    }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    analyzerExecutor,
                    QrAnalyzer { value, format ->
                        if (!currentPaused) currentOnResult(value, format)
                    },
                )
            }
    }

    // Resolve the provider asynchronously (no blocking .get() on main).
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = future.get() }, ContextCompat.getMainExecutor(context))
    }

    // (Re)bind whenever the provider resolves OR the lens changes.
    LaunchedEffect(provider, useFrontCamera) {
        val p = provider ?: return@LaunchedEffect
        val selector =
            if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        }
    }

    // Torch follows the setting (front cameras have no flash — runCatching guards it).
    LaunchedEffect(camera, torchEnabled) {
        runCatching { camera?.cameraControl?.enableTorch(torchEnabled) }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            runCatching { provider?.unbindAll() }
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })
}
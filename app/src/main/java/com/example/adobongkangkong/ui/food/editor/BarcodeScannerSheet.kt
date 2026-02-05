package com.example.adobongkangkong.ui.food.editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import androidx.camera.core.FocusMeteringAction

@Composable
fun BarcodeScannerSheet(
    onClose: () -> Unit,
    onBarcode: (String) -> Unit
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onClose) { Text("Close") }
        Spacer(Modifier.height(8.dp))

        if (!hasPermission) {
            Text("Camera permission is required to scan barcodes.")
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant permission")
            }
            return@Column
        }

        BarcodeScannerPreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            onBarcode = onBarcode
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun BarcodeScannerPreview(
    modifier: Modifier,
    onBarcode: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var handled by remember { mutableStateOf(false) }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS )
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        android.util.Log.d("SCAN", "frame ${imageProxy.width}x${imageProxy.height} rot=${imageProxy.imageInfo.rotationDegrees}")
                        if (handled) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        processImageProxy(scanner, imageProxy) { value ->
                            if (!handled && value.isNotBlank()) {
                                handled = true
                                onBarcode(value)
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )

                        previewView.post {
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(
                                previewView.width / 2f,
                                previewView.height / 2f
                            )
                            val action = FocusMeteringAction.Builder(point).build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }

                    } catch (_: Throwable) {
                        // ignore
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcode: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull()?.rawValue.orEmpty()
            Log.d("SCAN", "barcode=$raw")
            if (raw.isNotBlank()) onBarcode(raw)
        }
        .addOnFailureListener { /* ignore */ }
        .addOnCompleteListener { imageProxy.close() }
}


package com.example.adobongkangkong.ui.food.editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import com.example.adobongkangkong.core.log.MeowLog
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

/**
 * Bottom sheet UI that provides live barcode scanning using CameraX + ML Kit.
 *
 * ## Purpose
 * Allow users to scan food barcodes directly from the Food Editor flow to:
 * - import USDA foods by GTIN/UPC,
 * - assign barcodes to existing foods,
 * - create minimal placeholder foods when barcode data is unavailable.
 *
 * This sheet handles permission gating and preview lifecycle, while emitting
 * detected barcode values to the caller.
 *
 * ## Rationale (why this exists)
 * Barcode scanning is a camera-driven overlay similar to banner capture, but optimized
 * for continuous frame analysis instead of still capture.
 *
 * CameraX + ML Kit is used instead of external scanning apps to:
 * - keep the workflow in-app,
 * - guarantee deterministic handling of results,
 * - allow future custom overlay UI (bounding boxes, guides, etc.).
 *
 * ## Behavior
 * Permission handling:
 * - Checks camera permission on entry.
 * - If missing, prompts user to grant permission.
 * - Scanner preview starts only after permission is granted.
 *
 * Camera lifecycle:
 * - Creates a PreviewView and binds CameraX Preview + ImageAnalysis use cases.
 * - Uses ImageAnalysis to continuously receive frames.
 * - Feeds frames into ML Kit BarcodeScanner.
 *
 * Detection flow:
 * - First valid barcode value triggers [onBarcode].
 * - Further detections are ignored (`handled` guard) to prevent duplicate callbacks.
 *
 * Cleanup:
 * - On dispose, unbinds camera and closes ML Kit scanner.
 *
 * ## Parameters
 * - `onClose`: Called when user dismisses scanner manually.
 * - `onBarcode`: Called when a barcode string is successfully detected.
 *
 * ## Output guarantees
 * - Emits raw barcode string exactly as provided by ML Kit.
 * - Does not normalize, validate, or map barcode values.
 *
 * ## Edge cases
 * - Permission denied → scanner UI shows permission prompt instead of preview.
 * - Camera unavailable → failure logged; no crash expected.
 * - ML Kit may occasionally detect partial or incorrect values; caller must validate.
 *
 * ## Pitfalls / gotchas
 * - Multiple rapid detections are prevented via `handled` flag.
 * - Camera must be unbound on dispose to prevent conflicts with other camera features
 *   (e.g., BannerCaptureSheet).
 * - ImageAnalysis resolution impacts scan speed vs accuracy.
 *
 * ## Architectural rules
 * - UI-layer component.
 * - No database writes or navigation.
 * - Emits raw barcode value only; domain logic handles resolution/import.
 */
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onClose) { Text("Close") }
        Spacer(Modifier.height(8.dp))

        if (!hasPermission) {
            Text("Camera permission is required to scan barcodes.", modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera")
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

/**
 * CameraX preview + analysis pipeline that feeds frames into ML Kit.
 *
 * ## Purpose
 * Bind camera preview and frame analyzer to lifecycle and deliver decoded barcodes.
 *
 * ## Behavior
 * - Creates PreviewView.
 * - Binds Preview and ImageAnalysis use cases.
 * - Uses single-thread executor for analyzer.
 * - Automatically focuses on center of preview.
 *
 * ## Pitfalls
 * - Analyzer must always close ImageProxy or camera pipeline stalls.
 * - Camera must be unbound on dispose.
 */
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
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (t: Throwable) {
                MeowLog.e("BarcodeScannerSheet onDispose: unbindAll failed", t)
            }
            try {
                scanner.close()
            } catch (t: Throwable) {
                MeowLog.e("BarcodeScannerSheet onDispose: scanner.close failed", t)
            }
            cameraExecutor.shutdown()
        }
    }

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
                        Log.d("SCAN", "frame ${imageProxy.width}x${imageProxy.height}")
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

                        cameraProviderRef = cameraProvider

                    } catch (t: Throwable) {
                        MeowLog.e("BarcodeScannerSheet bind failed", t)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )
    }
}

/**
 * Converts ImageProxy into ML Kit InputImage and runs barcode detection.
 *
 * ## Guarantees
 * - Always closes ImageProxy.
 * - Emits first detected raw barcode value.
 *
 * ## Limitations
 * - Only returns first barcode per frame.
 * - Caller must handle duplicates across sessions.
 */
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

/**
 * FOR-FUTURE-ME / FOR-FUTURE-AI (BarcodeScannerSheet)
 *
 * Purpose:
 * - Continuous barcode scanning overlay for Food Editor.
 *
 * Invariants:
 * - ImageProxy must ALWAYS be closed.
 * - Camera must unbind on dispose.
 * - Only emit first detected barcode per open session.
 *
 * Do NOT:
 * - Add DB logic here.
 * - Add USDA import logic here.
 * - Block analyzer thread.
 *
 * If scanning stops working:
 * - Verify camera permission granted.
 * - Verify camera unbound from other sheets.
 * - Verify ImageProxy.close() always runs.
 */
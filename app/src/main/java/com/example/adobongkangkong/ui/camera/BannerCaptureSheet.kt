package com.example.adobongkangkong.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bottom sheet UI that captures a 3:1 “banner” image for a Food using CameraX.
 *
 * ## Purpose
 * Provide a controlled camera capture flow that:
 * - previews a banner-framed image (3:1 aspect ratio),
 * - captures a JPEG to app-private storage,
 * - fixes orientation deterministically,
 * - generates a blurred WebP derivative for UI backgrounds,
 * - returns a stable URI to the saved banner.
 *
 * This banner image becomes the canonical visual identity for a Food.
 *
 * ## Rationale (why this exists)
 * Foods in AdobongKangkong support visual banners used by:
 * - Food detail screens,
 * - Planner cards,
 * - Headers and hero backgrounds.
 *
 * Camera capture must be:
 * - deterministic (no silent transformations),
 * - lifecycle-safe (no camera leaks or conflicts),
 * - storage-consistent (canonical paths via FoodImageStorage),
 * - UI-consistent (preview framing matches saved intent).
 *
 * CameraX is used instead of implicit camera intents to:
 * - guarantee aspect ratio and crop intent,
 * - keep images fully private to the app,
 * - avoid OEM camera inconsistencies.
 *
 * ## Behavior
 * Lifecycle and binding:
 * - Requests camera permission if needed.
 * - Creates and owns a PreviewView.
 * - Binds CameraX Preview + ImageCapture use cases to the lifecycle.
 * - Uses a 3:1 ViewPort so preview framing matches banner layout.
 *
 * Capture flow:
 * - Ensures storage directories exist.
 * - Captures JPEG to banner file path.
 * - Fixes EXIF orientation by rotating pixels in place.
 * - Generates blurred WebP derivative (downscaled → upscaled blur effect).
 * - Returns banner URI via onCaptured.
 *
 * UI state handling:
 * - Shows loading indicator while binding.
 * - Disables capture during active capture.
 * - Cleans up camera resources when sheet is dismissed.
 *
 * ## Parameters
 * - `request`: Contains foodId and capture context.
 * - `onDismiss`: Called when sheet is closed.
 * - `onCaptured`: Called after successful capture with banner URI.
 * - `onError`: Called if any camera, IO, or processing failure occurs.
 *
 * ## Output guarantees
 * On success:
 * - banner JPEG exists at FoodImageStorage.bannerJpegFile(foodId)
 * - blur WebP exists at FoodImageStorage.bannerBlurWebpFile(foodId)
 * - returned URI points to the banner JPEG
 *
 * ## Edge cases
 * - Permission denied → capture disabled until granted.
 * - Camera binding failure → reported via onError.
 * - Decode or blur failure → banner still saved; blur may be missing.
 *
 * ## Pitfalls / gotchas
 * - PreviewView MUST be attached before binding CameraX or preview may be black.
 * - Multiple CameraX clients must unbind cleanly to avoid conflicts (e.g., barcode scanner).
 * - EXIF orientation must be normalized or images will render rotated inconsistently.
 *
 * ## Architectural rules
 * - UI-layer component; does not perform uploads or remote sync.
 * - Uses FoodImageStorage as single source of truth for paths.
 * - All camera and IO work runs off the main thread where appropriate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BannerCaptureSheet(
    request: BannerCaptureRequest,
    onDismiss: () -> Unit,
    onCaptured: (foodId: Long, uri: Uri) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val storage = remember { FoodImageStorage(context) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var isBinding by remember { mutableStateOf(hasCameraPermission) }
    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(previewView, hasCameraPermission) {
        if (!hasCameraPermission) {
            isBinding = false
            return@LaunchedEffect
        }
        isBinding = true
        runCatching {
            val provider = getCameraProvider(context, mainExecutor)
            cameraProvider = provider

            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(85)
                .setTargetRotation(rotation)
                .build()

            val viewPort = ViewPort.Builder(Rational(3, 1), rotation)
                .setScaleType(ViewPort.FILL_CENTER)
                .build()

            provider.unbindAll()

            val useCaseGroup = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(capture)
                .build()

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup
            )

            imageCapture = capture
        }.onFailure(onError)

        isBinding = false
    }

    DisposableEffect(previewView) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    factory = { previewView }
                )

                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "Camera permission is required to preview and capture a banner.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                if (isBinding) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss, enabled = !isCapturing) {
                    Text("Cancel")
                }

                Button(
                    enabled = !isCapturing && (!hasCameraPermission || (!isBinding && imageCapture != null)),
                    onClick = {
                        if (!hasCameraPermission) {
                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                            return@Button
                        }
                        val capture = imageCapture ?: return@Button
                        scope.launch {
                            isCapturing = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    storage.ensureAllBannerDirs(request.foodId)
                                    val bannerFile = storage.bannerJpegFile(request.foodId)
                                    val blurFile = storage.bannerBlurWebpFile(request.foodId)

                                    takePictureToFile(
                                        capture = capture,
                                        outputFile = bannerFile,
                                        mainExecutor = mainExecutor
                                    )
                                    fixJpegOrientationInPlace(bannerFile)

                                    generateBlurDerivative(
                                        inputJpeg = bannerFile,
                                        outputWebp = blurFile,
                                        webpQuality = 60,
                                        downscaleTargetWidthPx = 96
                                    )

                                    bannerFile.toUri()
                                }
                            }.onSuccess { uri ->
                                onCaptured(request.foodId, uri)
                            }.onFailure(onError)

                            isCapturing = false
                        }
                    }
                ) {
                    Text(
                        when {
                            !hasCameraPermission -> "Grant camera"
                            isCapturing -> "Capturing…"
                            else -> "Capture"
                        }
                    )
                }
            }
        }
    }
}

/**
 * Suspends until CameraProvider is available.
 *
 * ## Purpose
 * Convert CameraX’s callback-based initialization into a suspend function.
 *
 * ## Behavior
 * - Waits for ProcessCameraProvider future completion.
 * - Resumes normally with provider or throws exception.
 *
 * ## Notes
 * - Must run on mainExecutor per CameraX contract.
 */
private suspend fun getCameraProvider(
    context: android.content.Context,
    mainExecutor: Executor
): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        runCatching { future.get() }
            .onSuccess { cont.resume(it) }
            .onFailure { cont.resumeWithException(it) }
    }, mainExecutor)
}

/**
 * Captures a still image to a specific file.
 *
 * ## Guarantees
 * - Output file is fully written before returning.
 * - Throws ImageCaptureException on failure.
 */
private suspend fun takePictureToFile(
    capture: ImageCapture,
    outputFile: File,
    mainExecutor: Executor
) = suspendCancellableCoroutine<Unit> { cont ->
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    capture.takePicture(
        outputOptions,
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                cont.resume(Unit)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        }
    )
}

/**
 * Generates a blurred WebP derivative from the banner JPEG.
 *
 * ## Purpose
 * Provide a cheap, low-detail placeholder for backgrounds and transitions.
 *
 * ## Algorithm
 * - Decode source JPEG.
 * - Downscale to small width.
 * - Upscale back to original size → produces blur effect.
 * - Save as lossy WebP.
 *
 * ## Limitations
 * - Not a true Gaussian blur; relies on scaling artifact blur.
 * - Quality depends on downscale size.
 */
internal fun generateBlurDerivative(
    inputJpeg: File,
    outputWebp: File,
    webpQuality: Int,
    downscaleTargetWidthPx: Int
) { /* unchanged */ }

/**
 * Rotates JPEG pixels to match EXIF orientation and resets EXIF orientation tag.
 *
 * ## Purpose
 * Ensure banner renders consistently across all viewers.
 *
 * ## Important
 * After rotation, EXIF orientation is reset to NORMAL to prevent double rotation.
 */
private fun fixJpegOrientationInPlace(jpegFile: File) { /* unchanged */ }

/**
 * FOR-FUTURE-ME / FOR-FUTURE-AI (BannerCaptureSheet)
 *
 * This component is the canonical in-app camera capture for Food banners.
 *
 * Invariants:
 * - Preview and capture must share a 3:1 ViewPort.
 * - Banner JPEG is the source-of-truth image.
 * - Blur derivative is optional but expected by UI.
 * - Camera must always unbind on dispose.
 *
 * Do NOT:
 * - Move storage paths outside FoodImageStorage.
 * - Perform uploads here.
 * - Change capture aspect ratio without updating all banner UI assumptions.
 *
 * If preview goes black:
 * - Verify PreviewView is attached before binding.
 * - Verify no competing CameraX instance is bound.
 */
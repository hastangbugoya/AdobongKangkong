package com.example.adobongkangkong.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.example.adobongkangkong.feature.camera.BannerOwnerType
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
 * Bottom sheet UI that captures a 3:1 “banner” image using CameraX.
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
 * - `request`: Contains the banner owner (food or meal template).
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
    onCaptured: (owner: com.example.adobongkangkong.feature.camera.BannerOwnerRef, uri: Uri) -> Unit,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
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
                                    storage.ensureAllBannerDirs(request.owner)
                                    val bannerFile = storage.bannerJpegFile(request.owner)
                                    val blurFile = storage.bannerBlurWebpFile(request.owner)

                                    takePictureToFile(
                                        capture = capture,
                                        outputFile = bannerFile,
                                        mainExecutor = mainExecutor
                                    )

                                    // Normalize pixels + EXIF so all decoders render the same (BitmapFactory is NOT EXIF-aware).
                                    fixJpegOrientationInPlace(bannerFile)

                                    // Optional derivative used by list backgrounds; safe to regenerate.
                                    runCatching {
                                        generateBlurDerivative(
                                            inputJpeg = bannerFile,
                                            outputWebp = blurFile,
                                            webpQuality = 60,
                                            downscaleTargetWidthPx = 96
                                        )
                                    }.onFailure { t ->
                                        Log.w("Meow", "Banner blur generation failed: ${t.message}")
                                    }

                                    bannerFile.toUri()
                                }
                            }.onSuccess { uri ->
                                onCaptured(request.owner, uri)
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
 * - Decode source JPEG bounds to read original dimensions.
 * - Decode a downscaled bitmap (target width ~ downscaleTargetWidthPx).
 * - Upscale the downscaled bitmap to a moderate output size (blur-by-scaling artifact).
 * - Save as lossy WebP.
 *
 * ## Notes
 * - This does not need to match the original banner resolution; UI will scale/crop it.
 * - Output is stored in cacheDir and is safe to delete/regenerate.
 */
internal fun generateBlurDerivative(
    inputJpeg: File,
    outputWebp: File,
    webpQuality: Int,
    downscaleTargetWidthPx: Int
) {
    require(webpQuality in 0..100) { "webpQuality must be 0..100" }
    if (!inputJpeg.exists()) return

    // 1) Read bounds
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(inputJpeg.absolutePath, boundsOpts)
    val srcW = boundsOpts.outWidth
    val srcH = boundsOpts.outHeight
    if (srcW <= 0 || srcH <= 0) return

    // 2) Decode downscaled
    val sample = computeInSampleSize(srcW, srcH, downscaleTargetWidthPx.coerceAtLeast(16))
    val decodeOpts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val small = BitmapFactory.decodeFile(inputJpeg.absolutePath, decodeOpts) ?: return

    // 3) Upscale to create blur-by-scaling artifact.
    // Keep output moderate to avoid memory spikes; UI can still stretch/crop.
    val maxOutW = 768
    val outW = minOf(srcW, maxOutW)
    val outH = (outW.toFloat() * (srcH.toFloat() / srcW.toFloat())).toInt().coerceAtLeast(1)
    val blurred = Bitmap.createScaledBitmap(small, outW, outH, true)

    // 4) Write WebP
    outputWebp.parentFile?.mkdirs()
    FileOutputStream(outputWebp).use { out ->
        val format = when {
            Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.WEBP
        }
        blurred.compress(format, webpQuality, out)
        out.flush()
    }

    if (blurred !== small) blurred.recycle()
    small.recycle()
}

/**
 * Rotates JPEG pixels to match EXIF orientation and resets EXIF orientation tag.
 *
 * ## Purpose
 * Ensure banner renders consistently across all viewers.
 *
 * ## Important
 * After rotation, EXIF orientation is reset to NORMAL to prevent double rotation.
 *
 * ## Behavior
 * - Reads EXIF orientation.
 * - If rotation is required:
 *   - decodes bitmap,
 *   - rotates pixels,
 *   - overwrites the same file,
 *   - sets EXIF orientation to NORMAL.
 *
 * If EXIF is missing/unknown or decode fails, this is a no-op.
 */
private fun fixJpegOrientationInPlace(jpegFile: File) {
    if (!jpegFile.exists()) return

    val exif = runCatching { ExifInterface(jpegFile) }.getOrNull() ?: return
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    if (degrees == 0) {
        // Still normalize tag to be safe if something wrote a non-normal value that we don't handle.
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            runCatching { exif.saveAttributes() }
        }
        return
    }

    val bitmap = BitmapFactory.decodeFile(jpegFile.absolutePath) ?: return
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    FileOutputStream(jpegFile, false).use { out ->
        rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
    }

    bitmap.recycle()
    if (rotated !== bitmap) rotated.recycle()

    // Reset EXIF orientation to normal after pixel rotation.
    val exifOut = runCatching { ExifInterface(jpegFile) }.getOrNull() ?: return
    exifOut.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    runCatching { exifOut.saveAttributes() }
}

private fun computeInSampleSize(
    srcW: Int,
    srcH: Int,
    targetW: Int
): Int {
    if (srcW <= 0 || srcH <= 0) return 1
    if (targetW <= 0) return 1

    var inSampleSize = 1
    var halfW = srcW / 2
    var halfH = srcH / 2

    while (halfW / inSampleSize >= targetW && halfH / inSampleSize >= 1) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

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
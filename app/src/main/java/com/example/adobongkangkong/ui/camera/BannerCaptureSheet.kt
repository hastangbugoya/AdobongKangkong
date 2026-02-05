package com.example.adobongkangkong.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.ViewGroup
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BannerCaptureSheet(
    request: BannerCaptureRequest,
    onDismiss: () -> Unit,
    onCaptured: (foodId: Long, uri: Uri) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }

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

    var isBinding by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(previewView) {
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
                .setScaleType(ViewPort.FILL_CENTER) // cover crop
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
            )  {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    factory = { previewView }
                )

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
                    enabled = !isBinding && !isCapturing && imageCapture != null,
                    onClick = {
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
                                    Log.d("BannerCapture", "banner saved: ${bannerFile.absolutePath} exists=${bannerFile.exists()} size=${bannerFile.length()}")
                                    Log.d("BannerCapture", "blur target: ${blurFile.absolutePath} parentExists=${blurFile.parentFile?.exists()}")

                                    generateBlurDerivative(
                                        inputJpeg = bannerFile,
                                        outputWebp = blurFile,
                                        webpQuality = 60,
                                        downscaleTargetWidthPx = 96
                                    )

                                    Log.d("BannerCapture", "blur after gen: exists=${blurFile.exists()} size=${blurFile.length()}")

                                    bannerFile.toUri()
                                }
                            }.onSuccess { uri ->
                                onCaptured(request.foodId, uri)
                            }.onFailure(onError)

                            isCapturing = false
                        }
                    }
                ) {
                    Text(if (isCapturing) "Capturing…" else "Capture")
                }
            }
        }
    }
}

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

internal fun generateBlurDerivative(
    inputJpeg: File,
    outputWebp: File,
    webpQuality: Int,
    downscaleTargetWidthPx: Int
) {
    val src = BitmapFactory.decodeFile(inputJpeg.absolutePath)
    if (src == null) {
        Log.e("BannerCapture", "decodeFile failed: ${inputJpeg.absolutePath} exists=${inputJpeg.exists()} size=${inputJpeg.length()}")
        return
    }

    val targetW = downscaleTargetWidthPx.coerceAtLeast(1)
    val targetH = ((src.height * (targetW.toFloat() / src.width)).toInt()).coerceAtLeast(1)

    val small = Bitmap.createScaledBitmap(src, targetW, targetH, true)
    val blurred = Bitmap.createScaledBitmap(small, src.width, src.height, true)

    FileOutputStream(outputWebp).use { out ->
        val format = when {
            Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        blurred.compress(format, webpQuality.coerceIn(0, 100), out)
    }

    if (blurred !== src) blurred.recycle()
    if (small !== src) small.recycle()
    src.recycle()
}

private fun fixJpegOrientationInPlace(jpegFile: File) {
    runCatching {
        val exif = ExifInterface(jpegFile)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val rotateDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        if (rotateDegrees == 0) return

        val src = BitmapFactory.decodeFile(jpegFile.absolutePath) ?: return
        val m = Matrix().apply { postRotate(rotateDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)

        FileOutputStream(jpegFile).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        // After physically rotating pixels, reset EXIF orientation so viewers don't rotate again
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()

        if (rotated !== src) rotated.recycle()
        src.recycle()
    }.onFailure {
        Log.e("BannerCapture", "fixJpegOrientationInPlace failed", it)
    }
}
/**
 * FOR-FUTURE-ME (BannerCaptureSheet)
 *
 * Goal:
 * - Capture a “banner” photo for a Food (3:1 aspect), save to app storage,
 *   and generate a blurred derivative for UI backgrounds.
 *
 * IMPORTANT: CAMERA LIFECYCLE RULES
 * - PreviewView must be ATTACHED before binding CameraX, otherwise preview can be black.
 *   (Most common failure: binding in LaunchedEffect before AndroidView attaches.)
 * - Always unbind CameraX on dispose to prevent camera resource conflicts with other sheets
 *   (e.g., BarcodeScannerSheet).
 *
 * High-level flow:
 * 1) Create/own a PreviewView.
 * 2) Acquire ProcessCameraProvider (suspend helper).
 * 3) Build Preview + ImageCapture use cases.
 * 4) Bind to lifecycleOwner with a 3:1 ViewPort so the preview matches banner crop intent.
 * 5) On “Capture”:
 *    - save JPEG to banner file
 *    - fix orientation (EXIF / rotate-in-place)
 *    - generate blurred WebP derivative (small width for cheap UI blur)
 *    - notify via onCaptured(foodId, uri)
 *
 * Storage rules:
 * - Uses FoodImageStorage to determine canonical locations:
 *   - bannerJpegFile(foodId)
 *   - bannerBlurWebpFile(foodId)
 * - Call ensureAllBannerDirs(foodId) before writing.
 *
 * Output rules:
 * - Banner image is the “source of truth” for rendering.
 * - Blur derivative is optional but expected by the UI for nice placeholders.
 *
 * Known pitfalls:
 * - Black preview: binding before PreviewView is attached/measured.
 * - Conflicts with other CameraX uses (barcode scanner): ensure other camera sessions unbind on dispose.
 * - Rotation: previewView.display may be null early; don’t rely on it unless attached.
 *
 * “Locked-down” mindset:
 * - Keep capture deterministic and local: no uploads, no density guessing, no silent scaling.
 * - If capture fails, report via onError (don’t swallow).
 */


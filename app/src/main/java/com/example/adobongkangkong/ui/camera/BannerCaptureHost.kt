package com.example.adobongkangkong.ui.camera

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun BannerCaptureHost(
    controller: BannerCaptureController,
    onBannerSaved: (foodId: Long, uri: Uri) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val request by controller.request

    if (request != null) {
        BannerCaptureSheet(
            request = request!!,
            onDismiss = { controller.close() },
            onCaptured = { foodId, uri ->
                onBannerSaved(foodId, uri)
                controller.close()
            },
            onError = onError
        )
    }
}
/**
 * FOR-FUTURE-ME (host/renderer for the overlay)
 *
 * BannerCaptureHost is the *only* place that decides whether BannerCaptureSheet is shown.
 * It observes controller.request and renders the sheet when request != null.
 *
 * Control flow:
 * - controller.open(foodId) sets request -> host composes BannerCaptureSheet
 * - onCaptured -> notify caller via onBannerSaved + close() controller
 * - onDismiss -> close() controller
 *
 *  * Rules:
 *  * - Keep this file dumb: no nav, no DB, no camera binding.
 *  * - Errors bubble up via onError so callers can log or snackbar.
 *
 * Debugging checklist when banner capture “stops working”:
 * 1) Is this host actually mounted in the UI tree? (MainScreen should mount it)
 * 2) Is controller.open() being called?
 * 3) Are the caller and this host using the SAME controller instance?
 *  - If the sheet never appears: controller.request is never set (open() not called)
 *  - If the sheet appears then instantly disappears: something calls close() (dismiss, save, error)
 */



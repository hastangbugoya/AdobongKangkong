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

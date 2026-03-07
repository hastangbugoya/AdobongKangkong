package com.example.adobongkangkong.ui.camera

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.adobongkangkong.feature.camera.BannerOwnerRef

@Composable
fun BannerCaptureHost(
    controller: BannerCaptureController,
    onBannerSaved: (owner: BannerOwnerRef, uri: Uri) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val request by controller.request

    if (request != null) {
        BannerCaptureSheet(
            request = request!!,
            onDismiss = { controller.close() },
            onCaptured = { owner, uri ->
                onBannerSaved(owner, uri)
                controller.close()
            },
            onError = onError
        )
    }
}

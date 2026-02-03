package com.example.adobongkangkong.ui.camera

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class BannerCaptureRequest(
    val foodId: Long
)

/**
 * UI controller for the shared banner capture overlay.
 *
 * This class ONLY manages the open/close state + the current request.
 * CameraX binding + file saving happens inside BannerCaptureSheet.
 */
class BannerCaptureController {

    private val _request = mutableStateOf<BannerCaptureRequest?>(null)
    val request: State<BannerCaptureRequest?> = _request

    fun open(foodId: Long) {
        _request.value = BannerCaptureRequest(foodId = foodId)
    }

    fun close() {
        _request.value = null
    }
}

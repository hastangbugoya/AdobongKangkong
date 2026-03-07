package com.example.adobongkangkong.ui.camera

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.adobongkangkong.feature.camera.BannerOwnerRef
import com.example.adobongkangkong.feature.camera.BannerOwnerType

/**
 * Immutable request payload for launching banner capture.
 */
data class BannerCaptureRequest(
    val owner: BannerOwnerRef
)

class BannerCaptureController {

    private val _request = mutableStateOf<BannerCaptureRequest?>(null)
    val request: State<BannerCaptureRequest?> = _request

    fun open(foodId: Long) {
        open(BannerOwnerRef(BannerOwnerType.FOOD, foodId))
    }

    fun openMealTemplate(templateId: Long) {
        open(BannerOwnerRef(BannerOwnerType.TEMPLATE, templateId))
    }

    fun open(owner: BannerOwnerRef) {
        _request.value = BannerCaptureRequest(owner = owner)
    }

    fun close() {
        _request.value = null
    }
}

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
/**
 * FOR-FUTURE-ME (controller-only, no UI)
 *
 * This is intentionally dumb: it ONLY holds the current BannerCaptureRequest as Compose state.
 * It does not do CameraX, file IO, permissions, or saving.
 *
 * Locked-down rule:
 * - The controller must be a SINGLE shared instance, created once at app root (MainScreen).
 * - All screens that need banner capture must receive this instance via parameters (NavHost wiring),
 *   not by constructing their own.
 *
 * Why:
 * - BannerCaptureHost observes controller.request. If a screen uses a different controller instance,
 *   the host will never see open() calls and the camera sheet will never appear.
 *
 * - Single, app-wide controller that opens/closes the banner capture overlay.
 * - Holds the current BannerCaptureRequest (foodId) as Compose state.
 *
 * Mental model:
 * - This is NOT a camera class.
 * - This is a dumb UI state handle that lets any screen say “open banner capture for foodId=X”.
 * - The actual camera binding + capture happens inside BannerCaptureSheet.
 *
 * Invariants / rules:
 * - request == null means overlay is closed.
 * - request != null means BannerCaptureHost MUST render BannerCaptureSheet.
 * - Do not put business logic here; keep it UI-only to avoid lifecycle surprises.
 *
 * Common failure modes:
 * - If banner capture stops working, check that:
 *   1) MainScreen is creating a single controller instance (remember { ... })
 *   2) BannerCaptureHost is rendered at the top level (not inside a screen that can disappear)
 *   3) FoodEditorScreen is calling controller.open(foodId) with a non-null id
 */
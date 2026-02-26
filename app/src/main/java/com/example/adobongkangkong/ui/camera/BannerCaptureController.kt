package com.example.adobongkangkong.ui.camera

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Immutable request payload for launching banner capture.
 *
 * ## Purpose
 * Provide a minimal, serializable-ish data structure that uniquely identifies what the banner capture
 * overlay should capture for (currently: a Food by id).
 *
 * ## Notes
 * - Keep this intentionally small; anything derived should be computed by the caller or the sheet.
 */
data class BannerCaptureRequest(
    val foodId: Long
)

/**
 * UI controller for the shared banner capture overlay (open/close + request state only).
 *
 * ## Purpose
 * Act as the single source of truth for whether the banner capture sheet should be shown, and what
 * it should capture.
 *
 * ## Rationale (why this exists)
 * Banner capture is an overlay that may be triggered from many screens (food editor, details, etc.)
 * but must be rendered from one stable place in the UI tree ([BannerCaptureHost]).
 *
 * A dedicated controller:
 * - avoids passing “isSheetOpen” booleans through many composables,
 * - ensures consistent open/close semantics,
 * - prevents multiple competing sheet instances,
 * - makes debugging visibility issues straightforward (request state is the truth).
 *
 * This class is intentionally “dumb” to avoid lifecycle surprises:
 * - No CameraX binding
 * - No permissions
 * - No file IO
 * - No database writes
 *
 * All capture work happens inside [BannerCaptureSheet].
 *
 * ## Behavior
 * - Holds `request` as Compose state:
 *   - `null` means overlay closed.
 *   - non-null means overlay open and should be rendered.
 * - `open(foodId)` sets a new [BannerCaptureRequest].
 * - `close()` clears the request.
 *
 * ## Parameters
 * None. Controller is stateful and owned by the UI layer.
 *
 * ## Return
 * None. Consumers observe [request] and react in composition.
 *
 * ## Edge cases
 * - Calling `open()` repeatedly will replace the current request (last call wins).
 * - Calling `close()` is idempotent.
 *
 * ## Pitfalls / gotchas
 * - This controller must be a single shared instance mounted at the app root.
 *   If different screens construct their own controller instances, [BannerCaptureHost] will not
 *   observe changes and the sheet will never appear.
 *
 * ## Architectural rules
 * - UI-only state holder.
 * - Must not contain business logic or side effects.
 * - Ownership and scoping must be explicit (create once, pass down).
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
 * FOR-FUTURE-ME / FOR-FUTURE-AI (controller-only, no UI)
 *
 * This controller is intentionally dumb: it ONLY holds the current BannerCaptureRequest as Compose state.
 * It does not do CameraX, file IO, permissions, or saving.
 *
 * Locked-down rules / invariants:
 * - The controller must be a SINGLE shared instance, created once at app root (MainScreen).
 * - All screens that need banner capture must receive this instance via parameters (NavHost wiring),
 *   not by constructing their own.
 * - request == null means overlay is closed.
 * - request != null means BannerCaptureHost MUST render BannerCaptureSheet.
 * - Do not put business logic here; keep it UI-only to avoid lifecycle surprises.
 *
 * Why:
 * - BannerCaptureHost observes controller.request. If a screen uses a different controller instance,
 *   the host will never see open() calls and the camera sheet will never appear.
 *
 * Mental model:
 * - This is NOT a camera class.
 * - This is a dumb UI state handle that lets any screen say “open banner capture for foodId=X”.
 * - The actual camera binding + capture happens inside BannerCaptureSheet.
 *
 * Common failure modes (debug checklist):
 * 1) MainScreen is creating a single controller instance (remember { BannerCaptureController() })
 * 2) BannerCaptureHost is rendered at the top level (not inside a screen that can disappear)
 * 3) Call site is calling controller.open(foodId) with a valid (non-null) id
 * 4) If the sheet appears then closes instantly, something is calling close() (dismiss, success, or error path)
 */
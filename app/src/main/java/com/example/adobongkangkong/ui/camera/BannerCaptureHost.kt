package com.example.adobongkangkong.ui.camera

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Host/renderer for the banner capture overlay.
 *
 * ## Purpose
 * Render [BannerCaptureSheet] conditionally based on [BannerCaptureController.request], and wire
 * capture/dismiss callbacks back to the caller in a consistent, centralized way.
 *
 * ## Rationale (why this exists)
 * Camera capture is an overlay-style UI concern that must be:
 * - mounted once in the UI tree,
 * - controlled via a single source of truth,
 * - easy to reason about when debugging “sheet didn’t show” issues.
 *
 * This host ensures:
 * - the decision to show/hide the sheet is centralized (request != null),
 * - capture success always triggers the same close + callback semantics,
 * - dismissal always clears the controller state.
 *
 * This prevents scattered “show sheet” logic across multiple screens and reduces the chance of:
 * - multiple sheet instances,
 * - stale requests,
 * - conflicting controller ownership.
 *
 * ## Behavior
 * - Observes `controller.request`.
 * - When non-null:
 *   - renders [BannerCaptureSheet] with that request,
 *   - onDismiss closes the controller,
 *   - onCaptured notifies [onBannerSaved] and then closes the controller,
 *   - onError bubbles up to [onError] (controller is NOT auto-closed here; the sheet decides its own flow).
 *
 * ## Parameters
 * - `controller`: Overlay controller that holds the current capture request.
 * - `onBannerSaved`: Callback invoked on successful capture (foodId + banner Uri).
 * - `onError`: Callback invoked on failure; callers decide logging/snackbar behavior.
 *
 * ## Return
 * No return value. This is a composable host.
 *
 * ## Edge cases
 * - `controller.request == null` → renders nothing.
 * - If the sheet disappears immediately, something is likely calling `controller.close()`.
 *
 * ## Pitfalls / gotchas
 * - The host must be mounted in the UI tree for capture to work.
 * - Caller and host must share the same controller instance (common DI / remember ownership bug).
 * - Keep this host “dumb”: do not add navigation, DB writes, or camera logic here.
 *
 * ## Architectural rules
 * - UI-only renderer/adapter.
 * - Controller is the single source of truth for visibility.
 * - Errors must bubble to the caller; no silent swallowing.
 */
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
 * FOR-FUTURE-ME / FOR-FUTURE-AI (host/renderer for the overlay)
 *
 * BannerCaptureHost is the *only* place that decides whether BannerCaptureSheet is shown.
 * It observes controller.request and renders the sheet when request != null.
 *
 * Invariants:
 * - Visibility is driven only by controller.request (null == hidden, non-null == shown).
 * - onCaptured must always:
 *   1) notify caller via onBannerSaved(foodId, uri)
 *   2) close() the controller
 * - onDismiss must always close() the controller.
 * - Keep this file dumb: no nav, no DB, no camera binding.
 * - Errors bubble up via onError so callers can log or snackbar.
 *
 * Control flow:
 * - controller.open(foodId) sets request -> host composes BannerCaptureSheet
 * - onCaptured -> notify caller via onBannerSaved + close() controller
 * - onDismiss -> close() controller
 *
 * Debugging checklist when banner capture “stops working”:
 * 1) Is this host actually mounted in the UI tree? (MainScreen should mount it)
 * 2) Is controller.open() being called?
 * 3) Are the caller and this host using the SAME controller instance?
 *    - If the sheet never appears: controller.request is never set (open() not called)
 *    - If the sheet appears then instantly disappears: something calls close() (dismiss, save, error)
 */
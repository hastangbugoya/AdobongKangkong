package com.example.adobongkangkong

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.camera.BannerCaptureHost
import com.example.adobongkangkong.ui.navigation.AppNavHost
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun MainScreen() {
    val bannerCaptureController = remember { BannerCaptureController() }
    val navController = rememberNavController()
    val bannerRefreshTick = rememberSaveable { mutableIntStateOf(0) }

    AppNavHost(
        navController = navController,
        bannerCaptureController = bannerCaptureController,
        bannerRefreshTick = bannerRefreshTick.intValue
    )

    BannerCaptureHost(
        controller = bannerCaptureController,
        onBannerSaved = { foodId, uri ->
            bannerRefreshTick.intValue++
            Log.d("BannerCapture", "Food=$foodId Saved banner: $uri")
        },
        onError = { err ->
            Log.e("BannerCapture", "Capture error", err)
        }
    )
}

/**
 * FOR-FUTURE-ME (root overlay wiring)
 *
 * This screen is the “global host” for app-wide overlays that are NOT tied to a single destination.
 * Banner capture is one of those: it must survive navigation and be callable from any screen.
 *
 * Rules / invariants:
 * 1) There must be EXACTLY ONE BannerCaptureController instance for the whole app session.
 *    - It is created here with remember { BannerCaptureController() }.
 *    - NEVER create another controller inside a destination/composable (that would silently break the overlay).
 *
 * 2) BannerCaptureHost must be mounted at a stable root level (here), not inside a specific route.
 *    - The host is what actually renders the camera sheet when controller.request != null.
 *
 * 3) bannerRefreshTick exists only to force UI re-read of the banner file after capture.
 *    - FoodEditorScreen’s preview uses bannerRefreshTick as a key to decode the file again.
 *    - We increment tick in onBannerSaved so the editor refreshes immediately without navigation hacks.
 *
 * Debug mental model:
 * - If calling controller.open(foodId) does NOTHING, it's almost always because the caller is holding
 *   a different controller instance than the host is observing.
 */



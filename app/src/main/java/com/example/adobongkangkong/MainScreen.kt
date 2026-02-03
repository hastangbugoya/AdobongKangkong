package com.example.adobongkangkong

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.camera.BannerCaptureHost
import com.example.adobongkangkong.ui.navigation.AppNavHost

@Composable
fun MainScreen() {
    val bannerCaptureController = remember { BannerCaptureController() }
    val navController = rememberNavController()

    AppNavHost(
        navController = navController,
        bannerCaptureController = bannerCaptureController
    )

    BannerCaptureHost(
        controller = bannerCaptureController,
        onBannerSaved = { foodId, uri ->
            Log.d("BannerCapture", "Food=$foodId Saved banner: $uri")
        },
        onError = { err ->
            Log.e("BannerCapture", "Capture error", err)
        }
    )
}

package com.example.adobongkangkong

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.camera.BannerCaptureHost
import com.example.adobongkangkong.ui.navigation.AppNavHost

@Composable
fun MainScreen() {
    val bannerCaptureController = remember { BannerCaptureController() }
    val navController = rememberNavController()

    // NEW: changes whenever a banner is saved (forces downstream recomposition)
    var bannerRefreshTick by remember { mutableIntStateOf(0) }

    AppNavHost(
        navController = navController,
        bannerCaptureController = bannerCaptureController,
        bannerRefreshTick = bannerRefreshTick
    )

    BannerCaptureHost(
        controller = bannerCaptureController,
        onBannerSaved = { foodId, uri ->
            Log.d("BannerCapture", "Food=$foodId Saved banner: $uri")
            bannerRefreshTick++ // NEW
        },
        onError = { err ->
            Log.e("BannerCapture", "Capture error", err)
        }
    )
}


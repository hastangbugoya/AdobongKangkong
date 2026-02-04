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


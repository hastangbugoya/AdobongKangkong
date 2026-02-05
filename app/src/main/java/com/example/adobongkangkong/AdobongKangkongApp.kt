package com.example.adobongkangkong

import android.app.Application
import android.util.Log
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodByBarcodeUseCase
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AdobongKangkongApp: Application() {
    @Inject lateinit var importUsdaFoodByBarcodeUseCase: com.example.adobongkangkong.domain.usda.ImportUsdaFoodByBarcodeUseCase
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val okBarcode = "856262005105"
//        appScope.launch {
//            when (val r = importUsdaFoodByBarcodeUseCase(okBarcode)) {
//                is ImportUsdaFoodByBarcodeUseCase.Result.Success ->
//                    Log.d("USDA_TEST", "Imported foodId=${r.foodId}")
//
//                is ImportUsdaFoodByBarcodeUseCase.Result.Blocked ->
//                    Log.w("USDA_TEST", "Blocked: ${r.reason}")
//
//                is ImportUsdaFoodByBarcodeUseCase.Result.Failed ->
//                    Log.e("USDA_TEST", "Failed: ${r.message}")
//            }
//        }
    }
}

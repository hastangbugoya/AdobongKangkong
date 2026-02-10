package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

class ImportUsdaFoodByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService,
    private val importFromJson: ImportUsdaFoodFromSearchJsonUseCase
) {

    suspend operator fun invoke(barcode: String): Result {
        Log.d("Meow","ImportUsdaFoodByBarcodeUseCase> invoke $barcode")
        val cleaned = barcode.trim()
        if (cleaned.isBlank()) {
            return Result.Blocked("Blank barcode")
        }

        val json = usdaSearch.searchByBarcode(cleaned)
            ?: return Result.Failed("USDA search returned null response")

        return when (val r = importFromJson(json)) {
            is ImportUsdaFoodFromSearchJsonUseCase.Result.Success ->
                Result.Success(
                    r.foodId,
                    fdcId = r.fdcId,
                    gtinUpc = r.gtinUpc,
                    publishedDateIso = r.publishedDateIso,
                    modifiedDateIso = r.modifiedDateIso,
                )

            is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked ->
                Result.Blocked(r.reason)
        }
    }

    sealed class Result {
        data class Success(
            val foodId: Long,
            val fdcId: Long,
            val gtinUpc: String?,
            val publishedDateIso: String?,
            val modifiedDateIso: String?
        ) : Result()
        data class Blocked(val reason: String) : Result()
        data class Failed(val message: String) : Result()
    }
}

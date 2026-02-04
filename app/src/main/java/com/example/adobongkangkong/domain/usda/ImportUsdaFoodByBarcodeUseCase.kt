package com.example.adobongkangkong.domain.usda

import javax.inject.Inject

/**
 * Barcode feature entry point:
 * - Search USDA by barcode (gtinUpc)
 * - Import first result into DB
 *
 * No UI. Caller decides how to trigger/observe.
 */
class ImportUsdaFoodByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService,
    private val importFromJson: ImportUsdaFoodFromSearchJsonUseCase
) {
    suspend operator fun invoke(barcode: String): Result {
        val cleaned = barcode.trim()
        if (cleaned.isBlank()) return Result.Blocked("Blank barcode")

        val json = usdaSearch.searchByBarcode(cleaned)
            ?: return Result.Failed("USDA search returned null response")

        return when (val r = importFromJson(json)) {
            is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> Result.Success(r.foodId)
            is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> Result.Blocked(r.reason)
        }
    }

    sealed class Result {
        data class Success(val foodId: Long) : Result()
        data class Blocked(val reason: String) : Result()
        data class Failed(val message: String) : Result()
    }
}

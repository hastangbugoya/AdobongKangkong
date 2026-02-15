package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

class SearchUsdaFoodsByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService
) {
    suspend operator fun invoke(barcode: String): Result {
        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase > Invoke $barcode")
        val cleaned = barcode.trim()
        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase > barcode cleaned: $cleaned")
        if (cleaned.isBlank()) return Result.Blocked("Blank barcode")

        val json = usdaSearch.searchByBarcode(cleaned)
            ?: return Result.Failed("USDA search returned null response")
        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase> json from searchByBarcode> ${json.length}")

        val parsed = UsdaFoodsSearchParser.parse(json)
        val all = parsed.foods

        if (all.isEmpty()) return Result.Blocked("No results for barcode")

        // Prefer exact gtinUpc match
        val exact = all.filter { it.gtinUpc?.trim() == cleaned }
        val candidatesSource = if (exact.isNotEmpty()) exact else all

        val candidates = candidatesSource.map {
            PickItem(
                fdcId = it.fdcId,
                description = it.description?.trim().orEmpty(),
                brand = (it.brandName ?: it.brandOwner)?.trim().orEmpty(),
                servingText = it.householdServingFullText?.trim()
                    ?: run {
                        val ss = it.servingSize
                        val u = it.servingSizeUnit
                        if (ss != null && !u.isNullOrBlank()) "$ss $u" else ""
                    },
                gtinUpc = it.gtinUpc?.trim().orEmpty(),

                // ✅ NEW: required for resolver freshness rules
                publishedDateIso = it.publishedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
                modifiedDateIso = it.modifiedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
            )
        }

        Log.d(
            "Meow",
            "SearchUsdaFoodsByBarcodeUseCase> Result(barcode:$cleaned jason length: ${json.length} candidate.size:${candidates.size})"
        )
        return Result.Success(
            scannedBarcode = cleaned,
            searchJson = json,
            candidates = candidates
        )
    }

    data class PickItem(
        val fdcId: Long,
        val description: String,
        val brand: String,
        val servingText: String,
        val gtinUpc: String,

        // ✅ NEW
        val publishedDateIso: String?,
        val modifiedDateIso: String?,
    )

    sealed class Result {
        data class Success(
            val scannedBarcode: String,
            val searchJson: String,
            val candidates: List<PickItem>
        ) : Result()

        data class Blocked(val reason: String) : Result()
        data class Failed(val message: String) : Result()
    }
}
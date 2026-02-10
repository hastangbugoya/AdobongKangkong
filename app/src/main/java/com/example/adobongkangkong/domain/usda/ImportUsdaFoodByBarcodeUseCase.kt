package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

/**
 * Attempts to identify and import a USDA food using a barcode.
 *
 * Optimization (publishedDate gate)
 * --------------------------------
 * USDA search returns `publishedDate` per item. We treat publishedDate as the primary
 * "version gate". If the barcode already has a USDA mapping in `food_barcodes` with
 * a stored `usdaPublishedDateIso`, and the USDA search returns a publishedDate that
 * is NOT newer, we skip the expensive import step and return the existing mapped foodId.
 *
 * Notes:
 * - This optimization only applies when an existing mapping is source=USDA.
 * - USER_ASSIGNED mappings do not block USDA import; USDA may now recognize the barcode.
 * - Dates are ISO yyyy-MM-dd so lexicographic comparison works.
 */
class ImportUsdaFoodByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService,
    private val importFromJson: ImportUsdaFoodFromSearchJsonUseCase,
    private val barcodes: FoodBarcodeRepository
) {

    suspend operator fun invoke(barcode: String): Result {
        Log.d("Meow", "ImportUsdaFoodByBarcodeUseCase> invoke $barcode")

        val normalized = normalizeDigits(barcode)
        if (normalized.isBlank()) return Result.Blocked("Blank barcode")

        val json = usdaSearch.searchByBarcode(normalized)
            ?: return Result.Failed("USDA search returned null response")

        // ✅ Parse once to peek version/date + fdcId without importing
        val parsed = runCatching { UsdaFoodsSearchParser.parse(json) }.getOrNull()
            ?: return Result.Failed("USDA parse failed")

        val first = parsed.foods.firstOrNull()
            ?: return Result.Blocked("USDA search returned no foods")

        val newPublished = first.publishedDate?.trim()?.takeIf { it.isNotBlank() }
        val newModified = first.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        val newGtin = first.gtinUpc?.trim()?.takeIf { it.isNotBlank() }

        // Optimization only applies if we already have a USDA mapping for this barcode
        val existing = barcodes.getByBarcode(normalized)
        if (existing != null && existing.source == BarcodeMappingSource.USDA) {
            val oldPublished = existing.usdaPublishedDateIso?.trim()?.takeIf { it.isNotBlank() }

            // ISO yyyy-MM-dd compares lexicographically
            val isNewer = when {
                newPublished == null -> false
                oldPublished == null -> true
                else -> newPublished > oldPublished
            }

            if (!isNewer && existing.foodId > 0L) {
                return Result.Success(
                    foodId = existing.foodId,
                    fdcId = existing.usdaFdcId ?: first.fdcId,
                    gtinUpc = newGtin,
                    publishedDateIso = oldPublished ?: newPublished,
                    modifiedDateIso = newModified
                )
            }
        }

        // Full import path (writes Food + nutrients, returns metadata)
        return when (val r = importFromJson(json)) {
            is ImportUsdaFoodFromSearchJsonUseCase.Result.Success ->
                Result.Success(
                    foodId = r.foodId,
                    fdcId = r.fdcId,
                    gtinUpc = r.gtinUpc,
                    publishedDateIso = r.publishedDateIso,
                    modifiedDateIso = r.modifiedDateIso
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

    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) if (c in '0'..'9') sb.append(c)
        return sb.toString()
    }
}

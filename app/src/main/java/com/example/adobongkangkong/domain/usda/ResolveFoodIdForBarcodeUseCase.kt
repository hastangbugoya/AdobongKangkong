package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Barcode resolution precedence:
 * 1) Normalize digits
 * 2) Try USDA import
 *    - if success: ensure mapping points to USDA-backed food (USDA wins, overwrites USER_ASSIGNED)
 * 3) If USDA returns nothing/blocked/failed: fallback to local mapping (if present)
 */
class ResolveFoodIdForBarcodeUseCase @Inject constructor(
    private val importUsdaFoodByBarcode: ImportUsdaFoodByBarcodeUseCase,
    private val foods: FoodRepository,
    private val barcodes: FoodBarcodeRepository,
) {
    suspend operator fun invoke(rawBarcode: String, nowEpochMs: Long = System.currentTimeMillis()): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")

        // 1) Try USDA
        when (val usda = importUsdaFoodByBarcode(barcode)) {
            is ImportUsdaFoodByBarcodeUseCase.Result.Success -> {
                val foodId = usda.foodId

                // Pull USDA metadata from stored Food (import already persisted it)
                val food = foods.getById(foodId)

                // These property names should exist in your domain Food mapper since FoodEntity has them in v6.
                val usdaFdcId = tryGetLong(food, "usdaFdcId")
                val publishedDate = tryGetString(food, "usdaPublishedDate")

                // USDA wins: overwrite mapping for this barcode → points to USDA food
                val entity = FoodBarcodeEntity(
                    barcode = barcode,
                    foodId = foodId,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = usdaFdcId,
                    usdaPublishedDateIso = publishedDate,
                    assignedAtEpochMs = nowEpochMs,
                    lastSeenAtEpochMs = nowEpochMs,
                )
                barcodes.upsertAndTouch(entity, nowEpochMs)

                return Result.Resolved(
                    foodId = foodId,
                    normalizedBarcode = barcode,
                    source = BarcodeMappingSource.USDA
                )
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Blocked -> {
                // fall through to local mapping
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Failed -> {
                // fall through to local mapping
            }
        }

        // 2) Local fallback
        val mappedFoodId = barcodes.getFoodIdForBarcode(barcode)
            ?: return Result.NotFound(normalizedBarcode = barcode)

        barcodes.touchLastSeen(barcode, nowEpochMs)

        val mapping = barcodes.getByBarcode(barcode)
        return Result.Resolved(
            foodId = mappedFoodId,
            normalizedBarcode = barcode,
            source = mapping?.source ?: BarcodeMappingSource.USER_ASSIGNED
        )
    }

    sealed class Result {
        data class Resolved(
            val foodId: Long,
            val normalizedBarcode: String,
            val source: BarcodeMappingSource,
        ) : Result()

        data class NotFound(val normalizedBarcode: String) : Result()
        data class Blocked(val reason: String) : Result()
    }

    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) if (c in '0'..'9') sb.append(c)
        return sb.toString()
    }

    /**
     * Tight-scope fallback: we avoid depending on the Food model source file here.
     * If your domain Food exposes these properties directly (likely), we can replace these helpers
     * with `food.usdaFdcId` / `food.usdaPublishedDate` and delete reflection entirely.
     */
    private fun tryGetLong(food: Any?, prop: String): Long? {
        if (food == null) return null
        return try {
            val f = food::class.members.firstOrNull { it.name == prop } ?: return null
            (f.call(food) as? Long)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryGetString(food: Any?, prop: String): String? {
        if (food == null) return null
        return try {
            val f = food::class.members.firstOrNull { it.name == prop } ?: return null
            (f.call(food) as? String)
        } catch (_: Throwable) {
            null
        }
    }
}

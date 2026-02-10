package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Manual assignment path (when USDA returns nothing).
 *
 * Behavior:
 * - Normalizes digits.
 * - If barcode is currently mapped as USDA, block (USDA identity should remain authoritative).
 * - Otherwise upsert USER_ASSIGNED mapping.
 *
 * USDA can still overwrite USER_ASSIGNED later during scan resolution.
 */
class AssignBarcodeToFoodUseCase @Inject constructor(
    private val barcodes: FoodBarcodeRepository,
    private val upsert: UpsertBarcodeMappingUseCase,
) {
    suspend operator fun invoke(
        rawBarcode: String,
        foodId: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")
        if (foodId <= 0L) return Result.Blocked("Invalid foodId")

        val existing = barcodes.getByBarcode(barcode)
        if (existing?.source == BarcodeMappingSource.USDA) {
            return Result.Blocked("Barcode is already owned by USDA mapping; cannot user-assign.")
        }

        return when (val r = upsert(
            rawBarcode = barcode,
            foodId = foodId,
            source = BarcodeMappingSource.USER_ASSIGNED,
            nowEpochMs = nowEpochMs,
            usdaFdcId = null,
            usdaPublishedDateIso = null,
        )) {
            is UpsertBarcodeMappingUseCase.Result.Success ->
                Result.Success(barcode = r.barcode, foodId = r.foodId)

            is UpsertBarcodeMappingUseCase.Result.Blocked ->
                Result.Blocked(r.reason)
        }
    }

    sealed class Result {
        data class Success(val barcode: String, val foodId: Long) : Result()
        data class Blocked(val reason: String) : Result()
    }

    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) if (c in '0'..'9') sb.append(c)
        return sb.toString()
    }
}

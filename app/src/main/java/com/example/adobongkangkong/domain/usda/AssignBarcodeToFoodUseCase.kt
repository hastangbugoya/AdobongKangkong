package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Assigns a barcode to a target food through the USER_ASSIGNED/manual path and returns
 * a rich decision result for UI orchestration.
 *
 * Why this use case exists
 * - The editor flow needs more than a simple success/blocked answer.
 * - When a user scans a barcode while editing an existing food, the app must distinguish:
 *   1) barcode is brand new
 *   2) barcode is already assigned to this same food
 *   3) barcode is assigned to a different food
 *   4) barcode is USDA-owned and should not be silently remapped through the manual path
 *
 * This use case is intentionally narrower than future full barcode-adoption/remap logic.
 * It handles only the current USER_ASSIGNED/manual attach path.
 *
 * Current policy
 * - Blank barcode -> Blocked
 * - Invalid foodId -> Blocked
 * - Existing mapping on same food -> AlreadyAssignedToSameFood
 * - Existing USDA mapping on another food -> AssignedToOtherFood
 * - Existing USER_ASSIGNED mapping on another food -> AssignedToOtherFood
 * - No mapping -> create USER_ASSIGNED row via [UpsertBarcodeMappingUseCase]
 *
 * Important limitation
 * - This use case does NOT perform remap/tombstone behavior yet.
 * - It only detects that the barcode belongs to another food and returns that fact to the caller.
 * - The caller can then open the other food or later trigger an explicit remap flow.
 *
 * Architectural rule
 * - [UpsertBarcodeMappingUseCase] is the write primitive.
 * - This use case is the decision/orchestration layer for editor-friendly barcode assignment behavior.
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

        return when {
            existing == null -> {
                when (val r = upsert(
                    rawBarcode = barcode,
                    foodId = foodId,
                    source = BarcodeMappingSource.USER_ASSIGNED,
                    nowEpochMs = nowEpochMs,
                    usdaFdcId = null,
                    usdaPublishedDateIso = null,
                )) {
                    is UpsertBarcodeMappingUseCase.Result.Success ->
                        Result.AssignedNew(
                            barcode = r.barcode,
                            foodId = r.foodId,
                            source = r.source
                        )

                    is UpsertBarcodeMappingUseCase.Result.Blocked ->
                        Result.Blocked(r.reason)
                }
            }

            existing.foodId == foodId -> {
                barcodes.touchLastSeen(barcode, nowEpochMs)
                Result.AlreadyAssignedToSameFood(
                    barcode = barcode,
                    foodId = foodId,
                    source = existing.source
                )
            }

            else -> {
                Result.AssignedToOtherFood(
                    barcode = barcode,
                    currentFoodId = foodId,
                    existingFoodId = existing.foodId,
                    existingSource = existing.source
                )
            }
        }
    }

    sealed class Result {
        /**
         * Barcode was not previously mapped and is now newly assigned to the requested food.
         */
        data class AssignedNew(
            val barcode: String,
            val foodId: Long,
            val source: BarcodeMappingSource
        ) : Result()

        /**
         * Barcode was already mapped to the same target food.
         * Caller should usually open/reopen package editing rather than creating a duplicate row.
         */
        data class AlreadyAssignedToSameFood(
            val barcode: String,
            val foodId: Long,
            val source: BarcodeMappingSource
        ) : Result()

        /**
         * Barcode is already mapped to a different food.
         * Caller decides whether to open that food now or later start an explicit remap flow.
         */
        data class AssignedToOtherFood(
            val barcode: String,
            val currentFoodId: Long,
            val existingFoodId: Long,
            val existingSource: BarcodeMappingSource
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    /**
     * Normalizes barcode input into canonical numeric form.
     *
     * Behavior
     * - Trims whitespace
     * - Removes non-digit characters
     * - Preserves leading zeros
     */
    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) {
            if (c in '0'..'9') sb.append(c)
        }
        return sb.toString()
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Current scope
 * - Manual / USER_ASSIGNED barcode attach path only.
 * - No remap/tombstone behavior yet.
 * - No USDA import/package seeding behavior yet.
 *
 * Locked behavior
 * - New barcode -> AssignedNew
 * - Same-food barcode -> AlreadyAssignedToSameFood
 * - Other-food barcode -> AssignedToOtherFood
 * - Invalid input -> Blocked
 *
 * Important architecture note
 * - This use case is intentionally one level above UpsertBarcodeMappingUseCase.
 * - UpsertBarcodeMappingUseCase writes rows.
 * - AssignBarcodeToFoodUseCase decides what kind of situation the caller is in.
 *
 * Future planned evolution
 * - Explicit remap result
 * - Tombstone old row + create new row
 * - Deleted/merged-food handling
 * - USDA-first package override seeding
 */
package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Adopts a USDA-resolved barcode/package into an already-existing local food.
 *
 * Purpose
 * - Support the Food Editor flow where the user scans a package, USDA recognizes it,
 *   but the user does NOT want a second food created.
 * - Instead, the barcode/package should be attached to the existing target food and the
 *   USDA package information should seed the barcode row.
 *
 * What this use case does
 * - Normalizes the incoming barcode.
 * - Validates target food id.
 * - Checks whether the barcode is already mapped.
 * - Returns a rich result describing whether the barcode:
 *   - was newly adopted into the target food,
 *   - already belongs to the same food,
 *   - already belongs to a different food,
 *   - or was blocked by invalid input.
 *
 * Package seeding behavior
 * - Stores package-specific USDA values on the barcode row:
 *   - overrideHouseholdServingText
 *   - overrideServingSize
 *   - overrideServingUnit
 * - USDA metadata is stored on the barcode row:
 *   - usdaFdcId
 *   - usdaPublishedDateIso
 *   - usdaModifiedDateIso
 *
 * Important design rule
 * - Food remains the nutrition identity.
 * - Barcode row remains the package identity.
 * - This use case does NOT modify Food nutrient rows.
 * - This use case does NOT fill missing nutrients into the target food.
 *   That is a later step/use case.
 *
 * Current scope limitations
 * - No tombstoning yet.
 * - No deleted/merged-food recovery yet.
 * - No servings-per-package extraction from USDA text yet.
 * - If barcode belongs to another food, caller must decide how to proceed.
 *
 * Intended caller
 * - FoodEditor / barcode-adoption flow after USDA candidate has already been chosen.
 */
class AdoptUsdaBarcodePackageIntoFoodUseCase @Inject constructor(
    private val barcodes: FoodBarcodeRepository
) {

    suspend operator fun invoke(
        rawBarcode: String,
        targetFoodId: Long,
        usda: UsdaPackageSeed,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")
        if (targetFoodId <= 0L) return Result.Blocked("Invalid target foodId")
        if (usda.fdcId <= 0L) return Result.Blocked("Invalid USDA fdcId")

        val existing = barcodes.getByBarcode(barcode)

        return when {
            existing == null -> {
                val created = buildEntity(
                    barcode = barcode,
                    targetFoodId = targetFoodId,
                    usda = usda,
                    assignedAtEpochMs = nowEpochMs,
                    lastSeenAtEpochMs = nowEpochMs
                )

                barcodes.upsert(created)

                Result.AdoptedNew(
                    barcode = barcode,
                    foodId = targetFoodId
                )
            }

            existing.foodId == targetFoodId -> {
                val updated = existing.copy(
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = usda.fdcId,
                    usdaPublishedDateIso = usda.publishedDateIso,
                    usdaModifiedDateIso = usda.modifiedDateIso,
                    overrideServingsPerPackage = usda.overrideServingsPerPackage?.takeIf { it > 0.0 },
                    overrideHouseholdServingText = usda.householdServingText?.trim()?.ifBlank { null },
                    overrideServingSize = usda.overrideServingSize?.takeIf { it > 0.0 },
                    overrideServingUnit = usda.overrideServingUnit,
                    lastSeenAtEpochMs = nowEpochMs
                )

                barcodes.upsert(updated)

                Result.AlreadyOnSameFood(
                    barcode = barcode,
                    foodId = targetFoodId
                )
            }

            else -> {
                Result.AssignedToOtherFood(
                    barcode = barcode,
                    targetFoodId = targetFoodId,
                    existingFoodId = existing.foodId,
                    existingSource = existing.source
                )
            }
        }
    }

    /**
     * Minimal USDA package data needed to seed a barcode row.
     *
     * Notes
     * - serving/package info is barcode-row/package-level data, not food-level data.
     * - overrideServingsPerPackage is intentionally nullable and optional because USDA search
     *   results often do not expose a trustworthy package count directly.
     */
    data class UsdaPackageSeed(
        val fdcId: Long,
        val publishedDateIso: String? = null,
        val modifiedDateIso: String? = null,
        val householdServingText: String? = null,
        val overrideServingSize: Double? = null,
        val overrideServingUnit: ServingUnit? = null,
        val overrideServingsPerPackage: Double? = null,
    )

    sealed class Result {
        /**
         * Barcode was not previously mapped and is now adopted into the target food
         * with USDA package metadata seeded onto the barcode row.
         */
        data class AdoptedNew(
            val barcode: String,
            val foodId: Long
        ) : Result()

        /**
         * Barcode was already mapped to the same target food.
         * We refresh/upgrade the existing row with USDA metadata and package seed values.
         */
        data class AlreadyOnSameFood(
            val barcode: String,
            val foodId: Long
        ) : Result()

        /**
         * Barcode already belongs to a different food.
         * Caller must decide whether to open that food, prompt remap, or cancel.
         */
        data class AssignedToOtherFood(
            val barcode: String,
            val targetFoodId: Long,
            val existingFoodId: Long,
            val existingSource: BarcodeMappingSource
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    private fun buildEntity(
        barcode: String,
        targetFoodId: Long,
        usda: UsdaPackageSeed,
        assignedAtEpochMs: Long,
        lastSeenAtEpochMs: Long
    ): FoodBarcodeEntity {
        return FoodBarcodeEntity(
            barcode = barcode,
            foodId = targetFoodId,
            source = BarcodeMappingSource.USDA,
            usdaFdcId = usda.fdcId,
            usdaPublishedDateIso = usda.publishedDateIso,
            usdaModifiedDateIso = usda.modifiedDateIso,
            overrideServingsPerPackage = usda.overrideServingsPerPackage?.takeIf { it > 0.0 },
            overrideHouseholdServingText = usda.householdServingText?.trim()?.ifBlank { null },
            overrideServingSize = usda.overrideServingSize?.takeIf { it > 0.0 },
            overrideServingUnit = usda.overrideServingUnit,
            assignedAtEpochMs = assignedAtEpochMs,
            lastSeenAtEpochMs = lastSeenAtEpochMs
        )
    }

    /**
     * Digits-only normalization for canonical barcode storage.
     *
     * Preserves leading zeros.
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
 * Why this file exists
 * - This is the first domain step toward the "this scanned USDA package is actually my existing food"
 *   flow.
 * - It prevents the ViewModel from hand-assembling USDA package barcode rows directly.
 *
 * Locked current behavior
 * - New barcode on target food -> AdoptedNew + create USDA barcode row
 * - Existing same-food barcode -> AlreadyOnSameFood + upgrade row with USDA metadata/package fields
 * - Existing other-food barcode -> AssignedToOtherFood
 * - Invalid input -> Blocked
 *
 * Explicitly NOT handled yet
 * - Nutrient backfill from USDA food into target food
 * - Remap/tombstone of a barcode already owned by another food
 * - Deleted-food revival
 * - Merge-aware reassignment rules
 * - Package-count extraction from free text
 *
 * Architecture rules
 * - Food identity stays on FoodEntity.
 * - Package identity stays on FoodBarcodeEntity.
 * - This use case touches barcode rows only.
 *
 * Good next step
 * - Wire this use case from FoodEditorViewModel when the user picks a USDA candidate
 *   but chooses "use existing food" instead of importing a separate USDA food.
 */
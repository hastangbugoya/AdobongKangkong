package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodRepository
import java.util.UUID
import javax.inject.Inject

/**
 * CreateMinimalFoodWithBarcodeUseCase
 *
 * ## Purpose
 * Creates a new minimal, user-defined Food and immediately assigns a barcode mapping to it.
 *
 * ## Rationale
 * When barcode scanning fails to find a USDA match (or the user wants a placeholder immediately),
 * we still want to:
 * - let the user save *something* quickly,
 * - ensure the barcode is not “wasted” and will resolve next time,
 * - allow the user to enrich/merge the food details later.
 *
 * This use case provides the smallest valid Food record and a USER_ASSIGNED barcode mapping in one
 * operation.
 *
 * ## Behavior
 * 1) Builds a minimal [Food] with a new random [Food.stableId].
 * 2) Persists it via [FoodRepository.upsert] and obtains [foodId].
 * 3) Upserts a [FoodBarcodeEntity] row mapping [barcode] → [foodId] with:
 *    - source = [BarcodeMappingSource.USER_ASSIGNED]
 *    - assignedAtEpochMs = now
 *    - lastSeenAtEpochMs = now
 * 4) Returns the created/updated foodId.
 *
 * ## Assumptions / agreed rules
 * - The created Food is intentionally minimal:
 *   - servingSize = 1.0
 *   - servingUnit = [ServingUnit.SERVING]
 *   - gramsPerServingUnit/mlPerServingUnit = null
 *   - brand = null
 *   - USDA metadata fields = null
 * - The barcode mapping created here is always USER_ASSIGNED (not USDA).
 * - If [name] is blank, the food name becomes "Unnamed Food".
 *
 * ## Parameters
 * @param name Display name for the new food. Blank is allowed; it will be replaced by "Unnamed Food".
 * @param barcode Raw barcode string to map to the created food.
 *
 * ## Return
 * @return [Long] The persisted foodId (from [FoodRepository.upsert]).
 *
 * ## Ordering and edges
 * - This use case does not resolve collisions (e.g., barcode already mapped to another food).
 *   Collision policy is expected to be handled earlier in the barcode flow or within
 *   [FoodBarcodeDao.upsert] constraints.
 * - Timestamps use `System.currentTimeMillis()` for both `assignedAtEpochMs` and `lastSeenAtEpochMs`.
 */
class CreateMinimalFoodWithBarcodeUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val barcodeDao: FoodBarcodeDao
) {

    suspend operator fun invoke(
        name: String,
        barcode: String
    ): Long {

        val food = Food(
            id = 0L,
            stableId = UUID.randomUUID().toString(),
            name = name.ifBlank { "Unnamed Food" },
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = null,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        val foodId = foods.upsert(food)

        val now = System.currentTimeMillis()

        barcodeDao.upsert(
            FoodBarcodeEntity(
                barcode = barcode,
                foodId = foodId,
                source = BarcodeMappingSource.USER_ASSIGNED,
                usdaFdcId = null,
                usdaPublishedDateIso = null,
                assignedAtEpochMs = now,
                lastSeenAtEpochMs = now
            )
        )

        return foodId
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard use case documentation format in this codebase:
 *   - Top KDoc: purpose, rationale, behavior, params/return, assumptions, and known edges.
 *   - Bottom KDoc: constraints and “do not break” guidance for automated edits.
 *
 * - Do not “improve” minimal defaults unless explicitly requested.
 *   These defaults are intentional to keep creation frictionless after a failed scan.
 *
 * - This use case intentionally does NOT:
 *   - perform barcode collision resolution UI/logic,
 *   - import USDA data,
 *   - join/lookup nutrients,
 *   - set gramsPerServingUnit/mlPerServingUnit automatically.
 *
 * - If future work adds collision handling:
 *   - Keep this use case small; prefer a separate decision/use case layer.
 *   - Avoid introducing navigation or UI dependencies here.
 *
 * - If moving toward KMP later:
 *   - `UUID.randomUUID()` is JVM-specific; replace with a multiplatform id generator only when
 *     this file is actually moved into shared code.
 */
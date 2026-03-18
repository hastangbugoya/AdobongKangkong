package com.example.adobongkangkong.domain.usda

import android.util.Log
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import javax.inject.Inject

/**
 * Fills only MISSING nutrients on an existing Food using USDA search JSON, while preserving the
 * target Food's identity and existing nutrient basis.
 *
 * ============================================================================
 * HIGH-LEVEL PURPOSE
 * ============================================================================
 *
 * This use case exists for the "barcode adoption into existing food" workflow.
 *
 * Scenario:
 * - User is editing an existing food.
 * - User scans a barcode.
 * - USDA recognizes the package/item.
 * - User adopts that barcode/package into the CURRENT food instead of creating a new USDA food.
 * - User optionally chooses: "fill in missing nutrients from USDA."
 *
 * This use case performs ONLY that final nutrient step.
 *
 * It does NOT:
 * - create a new Food
 * - merge two foods
 * - overwrite existing nutrient values
 * - change barcode ownership / package rows
 * - update food name / brand / serving model / USDA metadata on the Food row
 *
 * ============================================================================
 * MENTAL MODEL
 * ============================================================================
 *
 * Food = nutrition identity
 * FoodBarcodeEntity = package identity
 *
 * In this flow:
 * - the target Food remains the canonical nutrition identity
 * - USDA acts only as a donor for nutrient rows that the target Food currently lacks
 *
 * Think of this as:
 * "fill in the blanks from USDA"
 *
 * NOT:
 * - "replace this food"
 * - "merge these foods"
 * - "turn this into a USDA food"
 *
 * ============================================================================
 * CORE RULES / INVARIANTS
 * ============================================================================
 *
 * 1) Existing target nutrient rows ALWAYS win.
 *    If the target food already has nutrient X, USDA nutrient X is skipped.
 *
 * 2) Target food basis is authoritative when target already has nutrients.
 *    USDA donor values must be normalized into the target's existing basis:
 *    - PER_100G
 *    - PER_100ML
 *    - USDA_REPORTED_SERVING
 *
 * 3) Mixed-basis target foods are rejected for now.
 *    If existing target nutrient rows already contain multiple basis types, this use case blocks
 *    rather than guessing which basis is authoritative.
 *
 * 4) If target has zero nutrients, USDA donor rows may seed the food.
 *    In that case there is no existing target basis to preserve yet, so the canonical USDA donor
 *    basis is accepted as the initial basis for inserted rows.
 *
 * 5) No density guessing.
 *    Never infer grams from mL.
 *    Never infer mL from grams.
 *    Never convert PER_100G <-> PER_100ML without a safe grounded path.
 *
 * 6) No duplicate nutrient rows are intentionally written.
 *    This use case de-dupes donor rows by nutrient id and inserts only nutrient ids not already
 *    present on the target food.
 *
 * ============================================================================
 * FLOW
 * ============================================================================
 *
 * Step 1 - Load target food + existing nutrients
 * Step 2 - Determine target basis state
 *   - no nutrients -> donor basis may seed food
 *   - one consistent basis -> donor must normalize into it
 *   - mixed basis -> block
 *
 * Step 3 - Parse USDA search JSON and select the requested USDA item
 *
 * Step 4 - Build canonical USDA donor rows
 *   This mirrors the import flow:
 *   - USDA nutrientNumber -> internal nutrient code
 *   - internal nutrient code -> Nutrient
 *   - start with USDA_REPORTED_SERVING rows
 *   - canonicalize to:
 *       PER_100G when safely mass-grounded
 *       PER_100ML when safely volume-grounded
 *       USDA_REPORTED_SERVING otherwise
 *
 * Step 5 - If target already has a basis, normalize donor rows into target basis
 *
 * Step 6 - Filter out nutrient ids already present on target
 *
 * Step 7 - Insert only remaining rows inside a DB transaction
 *
 * ============================================================================
 * RELATIONSHIP TO OTHER USDA FLOWS
 * ============================================================================
 *
 * ImportUsdaFoodFromSearchJsonUseCase
 * - creates or revives a USDA-backed Food and replaces its nutrients
 *
 * AdoptUsdaBarcodePackageIntoFoodUseCase
 * - associates package/barcode identity with an existing Food
 * - does not change nutrient rows
 *
 * This use case
 * - fills missing nutrients only
 * - keeps Food identity and target basis intact
 *
 * Future separate flow:
 * - a "replace current nutrients from USDA" use case should remain separate because it has the
 *   opposite overwrite contract from this one
 *
 * ============================================================================
 * FAILURE POLICY
 * ============================================================================
 *
 * This use case intentionally blocks rather than guessing when:
 * - target food does not exist
 * - target food is deleted or merged
 * - target food has mixed nutrient basis rows
 * - selected USDA item is missing
 * - USDA has no usable mapped nutrients
 * - donor values cannot be safely normalized into target basis
 *
 * Blocking is preferred over silently introducing mathematically suspicious data.
 */
class BackfillUsdaNutrientsIntoFoodUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val foodNutrients: FoodNutrientRepository,
    private val nutrients: NutrientRepository
) {

    suspend operator fun invoke(
        targetFoodId: Long,
        searchJson: String,
        selectedFdcId: Long? = null
    ): Result {
        val targetFood = db.foodDao().getById(targetFoodId)
            ?: return Result.Blocked("Target food not found (foodId=$targetFoodId).")

        if (targetFood.isDeleted) {
            return Result.Blocked("Target food is deleted and cannot be backfilled.")
        }

        if (targetFood.mergedIntoFoodId != null) {
            return Result.Blocked("Target food has been merged and cannot be backfilled.")
        }

        val existingRows = foodNutrients.getForFood(targetFoodId)
        val existingBasisTypes = existingRows
            .map { it.basisType }
            .distinct()

        val targetBasis: BasisType? = when {
            existingRows.isEmpty() -> null
            existingBasisTypes.size == 1 -> existingBasisTypes.first()
            else -> {
                return Result.Blocked(
                    "Target food has mixed nutrient basis types; backfill is blocked for now."
                )
            }
        }

        val parsed = UsdaFoodsSearchParser.parse(searchJson)
        val item = when (selectedFdcId) {
            null -> parsed.foods.firstOrNull()
            else -> parsed.foods.firstOrNull { it.fdcId == selectedFdcId }
        } ?: return Result.Blocked(
            if (selectedFdcId == null) "No foods in USDA response."
            else "Selected item not found in USDA response (fdcId=$selectedFdcId)."
        )

        val donorFood = buildUsdaDonorFood(item) ?: return Result.Blocked(
            "Unsupported USDA serving model for selected item (fdcId=${item.fdcId})."
        )

        val canonicalDonorRows = buildCanonicalUsdaRows(
            donorFood = donorFood,
            item = item
        )

        if (canonicalDonorRows.isEmpty()) {
            return Result.Blocked("USDA item has no usable mapped nutrients.")
        }

        val targetNormalizedRows = when (targetBasis) {
            null -> canonicalDonorRows
            else -> canonicalDonorRows.mapNotNull { row ->
                convertRowToTargetBasis(
                    row = row,
                    donorFood = donorFood,
                    targetBasis = targetBasis
                )
            }
        }

        if (targetBasis != null && targetNormalizedRows.size != canonicalDonorRows.size) {
            return Result.Blocked(
                "Cannot safely normalize USDA nutrients into target basis ($targetBasis)."
            )
        }

        val existingNutrientIds = existingRows
            .map { it.nutrient.id }
            .toSet()

        val rowsToInsert = targetNormalizedRows
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) -> group.firstOrNull() }
            .filter { it.nutrient.id !in existingNutrientIds }

        val skippedExistingCount = targetNormalizedRows.count { it.nutrient.id in existingNutrientIds }

        if (rowsToInsert.isEmpty()) {
            return Result.Success(
                targetFoodId = targetFoodId,
                insertedCount = 0,
                skippedExistingCount = skippedExistingCount,
                insertedNutrientIds = emptyList(),
                sourceFdcId = item.fdcId,
                appliedBasisType = targetBasis
            )
        }

        db.withTransaction {
            db.foodNutrientDao().upsertAll(
                rowsToInsert.map { row ->
                    FoodNutrientEntity(
                        foodId = targetFoodId,
                        nutrientId = row.nutrient.id,
                        nutrientAmountPerBasis = row.amount,
                        unit = row.nutrient.unit,
                        basisType = row.basisType
                    )
                }
            )
        }

        return Result.Success(
            targetFoodId = targetFoodId,
            insertedCount = rowsToInsert.size,
            skippedExistingCount = skippedExistingCount,
            insertedNutrientIds = rowsToInsert.map { it.nutrient.id },
            sourceFdcId = item.fdcId,
            appliedBasisType = targetBasis ?: rowsToInsert.first().basisType
        )
    }

    private suspend fun buildCanonicalUsdaRows(
        donorFood: Food,
        item: com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
    ): List<FoodNutrientRow> {
        val servingRows = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: return@mapNotNull null
            val nutrient = nutrients.getByCode(csvCode) ?: return@mapNotNull null
            val amount = n.value ?: return@mapNotNull null

            FoodNutrientRow(
                nutrient = nutrient,
                amount = amount,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        val canonicalRows = when {
            isMassGrounded(donorFood) -> {
                val gramsPerServing = computeGramsPerServing(donorFood) ?: return emptyList()
                val factor = 100.0 / gramsPerServing
                servingRows.map { row ->
                    row.copy(
                        basisType = BasisType.PER_100G,
                        amount = row.amount * factor,
                        basisGrams = 100.0
                    )
                }
            }

            isVolumeGrounded(donorFood) -> {
                val mlPerServing = computeMlPerServing(donorFood) ?: return emptyList()
                val factor = 100.0 / mlPerServing
                servingRows.map { row ->
                    row.copy(
                        basisType = BasisType.PER_100ML,
                        amount = row.amount * factor,
                        basisGrams = null
                    )
                }
            }

            else -> servingRows
        }

        return canonicalRows
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) -> group.firstOrNull() }
    }

    private fun convertRowToTargetBasis(
        row: FoodNutrientRow,
        donorFood: Food,
        targetBasis: BasisType
    ): FoodNutrientRow? {
        if (row.basisType == targetBasis) return row

        return when (targetBasis) {
            BasisType.PER_100G -> convertRowToPer100G(row, donorFood)
            BasisType.PER_100ML -> convertRowToPer100Ml(row, donorFood)
            BasisType.USDA_REPORTED_SERVING -> convertRowToUsdaReportedServing(row, donorFood)
        }
    }

    private fun convertRowToPer100G(
        row: FoodNutrientRow,
        donorFood: Food
    ): FoodNutrientRow? {
        val gramsPerServing = computeGramsPerServing(donorFood) ?: return null

        return when (row.basisType) {
            BasisType.PER_100G -> row
            BasisType.USDA_REPORTED_SERVING -> {
                row.copy(
                    amount = row.amount * (100.0 / gramsPerServing),
                    basisType = BasisType.PER_100G,
                    basisGrams = 100.0
                )
            }
            BasisType.PER_100ML -> null
        }
    }

    private fun convertRowToPer100Ml(
        row: FoodNutrientRow,
        donorFood: Food
    ): FoodNutrientRow? {
        val mlPerServing = computeMlPerServing(donorFood) ?: return null

        return when (row.basisType) {
            BasisType.PER_100ML -> row
            BasisType.USDA_REPORTED_SERVING -> {
                row.copy(
                    amount = row.amount * (100.0 / mlPerServing),
                    basisType = BasisType.PER_100ML,
                    basisGrams = null
                )
            }
            BasisType.PER_100G -> null
        }
    }

    private fun convertRowToUsdaReportedServing(
        row: FoodNutrientRow,
        donorFood: Food
    ): FoodNutrientRow? {
        return when (row.basisType) {
            BasisType.USDA_REPORTED_SERVING -> row
            BasisType.PER_100G -> {
                val gramsPerServing = computeGramsPerServing(donorFood) ?: return null
                row.copy(
                    amount = row.amount * (gramsPerServing / 100.0),
                    basisType = BasisType.USDA_REPORTED_SERVING,
                    basisGrams = null
                )
            }
            BasisType.PER_100ML -> {
                val mlPerServing = computeMlPerServing(donorFood) ?: return null
                row.copy(
                    amount = row.amount * (mlPerServing / 100.0),
                    basisType = BasisType.USDA_REPORTED_SERVING,
                    basisGrams = null
                )
            }
        }
    }

    private fun buildUsdaDonorFood(
        item: com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
    ): Food? {
        val brand: String? =
            item.brandName?.trim()?.toTitleCase().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim()?.takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val isGenericUsdaItem = item.gtinUpc.isNullOrBlank() &&
                item.brandName.isNullOrBlank() &&
                item.brandOwner.isNullOrBlank()

        val parsedUnit = ServingUnit.fromUsda(item.servingSizeUnit)
        val resolvedServing: Pair<Double, ServingUnit> = when {
            parsedUnit != null -> {
                (item.servingSize ?: 1.0) to parsedUnit
            }

            isGenericUsdaItem -> {
                Log.w(
                    "USDA_BACKFILL",
                    "Missing USDA servingSizeUnit for generic item fdcId=${item.fdcId}; defaulting to 100 g."
                )
                100.0 to ServingUnit.G
            }

            else -> {
                return null
            }
        }

        val rawServingSize = resolvedServing.first
        val rawServingUnit = resolvedServing.second
        val household = parseHouseholdServing(item.householdServingFullText)

        val finalServingSize = household?.size ?: rawServingSize
        val finalServingUnit = household?.unit ?: rawServingUnit

        val gramsPerServingUnit = computeGramsBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        val mlPerServingUnit = computeMlBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        return Food(
            id = 0L,
            name = normalizedName,
            servingSize = finalServingSize,
            servingUnit = finalServingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            stableId = "usda:temp:${item.fdcId}",
            brand = brand,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            usdaPublishedDate = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            usdaModifiedDate = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun isMassGrounded(food: Food): Boolean {
        return food.servingUnit.isMassUnit() || (food.gramsPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun isVolumeGrounded(food: Food): Boolean {
        if (isMassGrounded(food)) return false
        return food.servingUnit.isVolumeUnit() || (food.mlPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun computeGramsPerServing(food: Food): Double? {
        val bridgedGramsPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGramsPer1 != null -> food.servingSize * bridgedGramsPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }

    private data class HouseholdServing(
        val size: Double,
        val unit: ServingUnit
    )

    private fun parseHouseholdServing(text: String?): HouseholdServing? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val head = trimmed.substringBefore("(").trim()
        val parts = head.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val size = parts[0].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val unitText = parts.drop(1).joinToString(" ").trim()
        val unit = ServingUnit.fromUsda(unitText) ?: return null

        return HouseholdServing(size = size, unit = unit)
    }

    private fun computeGramsBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isMassUnit()) return null
        if (displayUnit.isMassUnit()) return null

        val gramsTotal = rawUnit.toGrams(rawSize)?.takeIf { it > 0.0 } ?: return null
        return (gramsTotal / householdSize).takeIf { it > 0.0 }
    }

    private fun computeMlBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isVolumeUnit()) return null

        val mlTotal = rawUnit.toMilliliters(rawSize)?.takeIf { it > 0.0 } ?: return null
        return (mlTotal / householdSize).takeIf { it > 0.0 }
    }

    sealed class Result {
        data class Success(
            val targetFoodId: Long,
            val insertedCount: Int,
            val skippedExistingCount: Int,
            val insertedNutrientIds: List<Long>,
            val sourceFdcId: Long,
            val appliedBasisType: BasisType?
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }
}

/**
 * ===== Bottom KDoc (for future dev / AI assistant) =====
 *
 * What this file is intentionally strict about
 * - Backfill means INSERT MISSING ONLY.
 * - Existing target nutrient values must never be overwritten here.
 * - Target food identity must remain intact.
 * - Barcode/package adoption remains a separate concern.
 * - Target basis is preserved when target already has nutrient rows.
 *
 * Why this use case currently duplicates some USDA serving + canonicalization logic
 * - Today, the import flow and this backfill flow both need the same USDA normalization rules.
 * - This file currently mirrors the locked-in import behavior so basis math stays aligned.
 * - A later refactor may extract a shared internal USDA donor-row builder, but only if behavior
 *   remains byte-for-byte equivalent in the important paths.
 *
 * Future improvements
 * 1) Extract shared USDA donor-row canonicalization logic used by:
 *    - ImportUsdaFoodFromSearchJsonUseCase
 *    - BackfillUsdaNutrientsIntoFoodUseCase
 *    - future ReplaceFoodNutrientsFromUsdaUseCase
 *
 * 2) Add unit tests covering:
 *    - target has no nutrients
 *    - target has consistent PER_100G basis
 *    - target has consistent PER_100ML basis
 *    - target has USDA_REPORTED_SERVING basis
 *    - target has mixed basis -> blocked
 *    - donor needs unsafe grams<->mL inference -> blocked
 *    - existing nutrient rows are never overwritten
 *    - donor sparse coverage
 *    - duplicate USDA nutrient numbers
 *
 * 3) Add a future replace flow as a SEPARATE use case:
 *    - ReplaceFoodNutrientsFromUsdaUseCase
 *    - preserve target basis
 *    - intentionally overwrite existing nutrient values
 *    - probably keep nutrients USDA does not provide unless product rules later say otherwise
 *
 * 4) Consider a richer Result model if UI later needs:
 *    - skipped nutrient ids
 *    - blocked reason categories
 *    - unmapped USDA nutrient counts
 *
 * Concise refactor rules
 * - Do not merge this use case into barcode adoption logic.
 * - Do not collapse "backfill missing" and "replace from USDA" into one use case.
 * - Do not refactor shared USDA logic unless behavior is preserved and covered by tests.
 * - Prefer extraction of pure helpers over changing persistence semantics.
 *
 * Concise KDoc rules
 * - Keep the top KDoc focused on behavior, invariants, flow, and architectural boundaries.
 * - Keep the bottom KDoc focused on maintenance notes, future work, and refactor warnings.
 * - When changing business rules, update both top and bottom KDoc in the same pass.
 *
 * Concise identifier rules
 * - Use "target" for the existing local Food being preserved.
 * - Use "donor" for USDA-derived temporary nutrient data.
 * - Reserve "replace" for overwrite flows only.
 * - Reserve "backfill" for insert-missing-only behavior.
 * - Avoid vague names like "merge" or "sync" here; they obscure overwrite semantics.
 */
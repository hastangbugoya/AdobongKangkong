package com.example.adobongkangkong.domain.usda

import android.util.Log
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
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
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Imports a USDA `/foods/search` result into the local database, canonicalizing nutrients and serving bridges.
 *
 * ============================================================
 * DATA FLOW DIAGRAM (DFD)
 * ============================================================
 *
 * USDA `/foods/search` JSON
 *        │
 *        ▼
 * UsdaFoodsSearchParser.parse()
 *        │
 *        ▼
 * Select USDA item (fdcId)
 *        │
 *        ▼
 * Build Food draft
 *   - servingSize
 *   - servingUnit
 *   - gramsPerServingUnit (mass bridge)
 *   - mlPerServingUnit (volume bridge)
 *   - stableId (gtin or fdc)
 *        │
 *        ▼
 * Map USDA nutrients → internal nutrient catalog
 *        │
 *        ▼
 * Canonicalize nutrient basis
 *   ├─ mass-grounded → PER_100G
 *   ├─ volume-grounded → PER_100ML
 *   └─ otherwise → USDA_REPORTED_SERVING
 *        │
 *        ▼
 * DB transaction
 *   ├─ FoodRepository.upsert()
 *   └─ FoodNutrientRepository.replaceForFood()
 *        │
 *        ▼
 * RETURN foodId + USDA metadata
 *
 * ============================================================
 *
 * Purpose
 * - Convert USDA search response JSON into a fully usable FoodEntity and nutrient snapshot.
 * - Normalize nutrients into a canonical storage basis for safe scaling and recipe math.
 * - Establish serving bridges (gramsPerServingUnit or mlPerServingUnit) when USDA provides safe grounding.
 *
 * Rationale (why this use case exists)
 * - USDA responses provide nutrients relative to a serving size, which may not be safely scalable.
 * - The app requires a canonical, normalized basis (PER_100G or PER_100ML) for:
 *   - recipe math
 *   - logging
 *   - planner projections
 *   - nutrient scaling
 * - This use case performs that normalization while preserving original serving semantics for UI.
 *
 * Core invariants enforced here
 *
 * ONE-basis rule
 * - Exactly one row per (foodId, nutrientId).
 * - Never store multiple basis rows for same nutrient.
 *
 * Mass grounding rule
 * - If USDA serving unit is mass-based OR grams bridge exists:
 *     canonical basis = PER_100G
 *
 * Volume grounding rule
 * - If USDA serving unit is volume-based OR mL bridge exists:
 *     canonical basis = PER_100ML
 *
 * Raw fallback rule
 * - If neither mass nor volume grounding exists:
 *     store USDA_REPORTED_SERVING only.
 *
 * Absolute safety rules
 * - NEVER infer grams from mL.
 * - NEVER infer mL from grams.
 * - NEVER guess density.
 *
 * Revive-on-import behavior
 * - If a food with same usdaFdcId already exists:
 *     reuse its row id and stableId.
 *
 * This prevents:
 * - duplicate foods
 * - broken log references
 *
 * Household serving bridge logic
 *
 * USDA example:
 *
 *     servingSize = 473
 *     servingUnit = MLT
 *     householdServingFullText = "1 can (473 mL)"
 *
 * Stored as:
 *
 *     servingSize = 1
 *     servingUnit = CAN
 *     mlPerServingUnit = 473
 *
 * This preserves:
 *
 * UI display:
 *     "1 can"
 *
 * mathematical truth:
 *     1 can = 473 mL
 *
 * Import steps
 *
 * Step 1 — Parse USDA JSON
 * Uses UsdaFoodsSearchParser.
 *
 * Step 2 — Select target USDA item
 * Either first item or selectedFdcId.
 *
 * Step 3 — Detect revive scenario
 * Existing usdaFdcId → reuse id and stableId.
 *
 * Step 4 — Resolve serving model
 * Determine:
 *     servingSize
 *     servingUnit
 *     gramsPerServingUnit bridge
 *     mlPerServingUnit bridge
 *
 * Step 5 — Map USDA nutrients
 * USDA nutrientNumber → CSV code → internal nutrient.
 *
 * Step 6 — Canonicalize nutrient basis
 * Convert serving-based nutrients into PER_100G or PER_100ML when grounded.
 *
 * Step 7 — Transactional persistence
 * Upsert FoodEntity.
 * Replace nutrient rows atomically.
 *
 * Parameters
 * - searchJson:
 *   USDA foods/search JSON string.
 *
 * - selectedFdcId:
 *   Optional specific USDA food to import.
 *
 * Return
 * - Success:
 *   Food successfully imported or revived.
 *
 * - Blocked:
 *   Unsupported serving unit
 *   USDA parsing/select failure
 *
 * Edge cases handled
 * - branded foods with custom serving units
 * - household serving text overriding display unit
 * - existing USDA foods being re-imported safely
 * - partial nutrient catalogs
 *
 * Pitfalls / gotchas
 * - householdServingFullText overrides display unit but NOT underlying truth.
 * - mlPerServingUnit and gramsPerServingUnit bridges are critical for canonicalization.
 *
 * Architectural rules
 * - Must never write duplicate nutrient rows.
 * - Must always use DB transaction.
 *
 * Logging model compatibility
 * - Imported foods become valid inputs for:
 *     snapshot logging
 *     planner
 *     recipes
 */
class ImportUsdaFoodFromSearchJsonUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository,
    private val nutrients: NutrientRepository
) {
    suspend operator fun invoke(searchJson: String, selectedFdcId: Long? = null): Result {
        val parsed = UsdaFoodsSearchParser.parse(searchJson)

        val item = when (selectedFdcId) {
            null -> parsed.foods.firstOrNull()
            else -> parsed.foods.firstOrNull { it.fdcId == selectedFdcId }
        } ?: return Result.Blocked(
            if (selectedFdcId == null) "No foods in USDA response."
            else "Selected item not found in USDA response (fdcId=$selectedFdcId)."
        )

        val computedStableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }

        // ♻️ Revive-on-import (critical for UNIQUE usdaFdcId)
        val existing = db.foodDao().getByUsdaFdcId(item.fdcId)
        val revivedId = existing?.id ?: 0L
        val finalStableId = existing?.stableId ?: computedStableId

        val brand: String? =
            item.brandName?.trim()?.toTitleCase().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val isGenericUsdaItem = item.gtinUpc.isNullOrBlank() &&
                item.brandName.isNullOrBlank() &&
                item.brandOwner.isNullOrBlank()

        val resolvedServing: Pair<Double, ServingUnit> = run {
            val parsedUnit = ServingUnit.fromUsda(item.servingSizeUnit)

            if (parsedUnit != null) {
                parsedUnit to parsedUnit // placeholder shape not used
            }

            when {
                parsedUnit != null -> {
                    val rawServingSize = item.servingSize ?: 1.0
                    rawServingSize to parsedUnit
                }

                isGenericUsdaItem -> {
                    Log.w(
                        "USDA_IMPORT",
                        "Missing USDA servingSizeUnit for generic item fdcId=${item.fdcId}; defaulting to 100 g."
                    )
                    100.0 to ServingUnit.G
                }

                else -> {
                    return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")
                }
            }
        }

        val rawServingSize: Double = resolvedServing.first
        val rawServingUnit: ServingUnit = resolvedServing.second

        val household = parseHouseholdServing(item.householdServingFullText)

        // LOCKED-IN USDA RULE:
        // If household text is parseable, we store servingSize=household.size and servingUnit=household.unit for display.
        val finalServingSize: Double = household?.size ?: rawServingSize
        val finalServingUnit: ServingUnit = household?.unit ?: rawServingUnit

        Log.d("USDA_IMPORT", "household=$household rawServingSize=$rawServingSize rawServingUnit=$rawServingUnit")

        val gramsPerServingUnit: Double? = computeGramsBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        val mlPerServingUnit: Double? = computeMlBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        val food = Food(
            id = revivedId,
            name = normalizedName,
            servingSize = finalServingSize,
            servingUnit = finalServingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            stableId = finalStableId.ifBlank { UUID.randomUUID().toString() },
            brand = brand,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            usdaPublishedDate = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            usdaModifiedDate = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )

        val servingRows: List<FoodNutrientRow> = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: return@mapNotNull null

            val nutrient = nutrients.getByCode(csvCode) ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null

            FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        // Canonicalize to ONE basis list (no duplicates per nutrientId)
        val canonicalRows: List<FoodNutrientRow> = when {
            // Mass-grounded: PER_100G (grams only)
            isMassGrounded(food) -> {
                val gramsPerServing = computeGramsPerServing(food) ?: return Result.Blocked(
                    "USDA import: failed to compute grams-per-serving for mass-grounded food."
                )
                val factor = 100.0 / gramsPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100G,
                        amount = r.amount * factor,
                        basisGrams = 100.0,
                    )
                }
            }

            // Volume-grounded: PER_100ML (mL only)
            isVolumeGrounded(food) -> {
                val mlPerServing = computeMlPerServing(food) ?: return Result.Blocked(
                    "USDA import: failed to compute ml-per-serving for volume-grounded food."
                )
                val factor = 100.0 / mlPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100ML,
                        amount = r.amount * factor,
                        basisGrams = null
                    )
                }
            }

            else -> servingRows
        }
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) -> group.firstOrNull() }

        val foodId = db.withTransaction {
            val id = foods.upsert(food)
            foodNutrients.replaceForFood(id, canonicalRows)
            id
        }

        return Result.Success(
            foodId = foodId,
            fdcId = item.fdcId,
            gtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            publishedDateIso = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            modifiedDateIso = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun isMassGrounded(food: Food): Boolean {
        return food.servingUnit.isMassUnit() || (food.gramsPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun isVolumeGrounded(food: Food): Boolean {
        // Must not claim volume grounding if mass grounding exists (single-grounding invariant).
        if (isMassGrounded(food)) return false
        return food.servingUnit.isVolumeUnit() || (food.mlPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun computeGramsPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        // This is required for USDA liquids where servingUnit may be CUP/FLOZ/etc but USDA servingSize in mL is authoritative.
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

    /**
     * Parses USDA householdServingFullText like:
     * - "1 can"
     * - "1 can (473 mL)"
     * - "2 tbsp"
     *
     * We only care about the leading "{number} {unitWord...}" part.
     */
    private fun parseHouseholdServing(text: String?): HouseholdServing? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()

        // Keep only the part before '(' if present: "1 can (473 mL)" -> "1 can"
        val head = trimmed.substringBefore("(").trim()

        // Very small parser: number + unit token(s)
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

        // If the display unit itself is a mass unit, we can rely on deterministic conversion; no bridge needed.
        if (displayUnit.isMassUnit()) return null

        val gramsTotal = rawUnit.toGrams(rawSize)?.takeIf { it > 0.0 } ?: return null

        // Bridge must be per 1 display-unit (since we persist servingSize=1 when household is parseable).
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

        // Bridge must be per 1 display-unit (since we persist servingSize=1 when household is parseable).
        return (mlTotal / householdSize).takeIf { it > 0.0 }
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
    }
}

/**
 * Title-cases a string for display.
 *
 * NOTE:
 * - This is intentionally simple and locale-agnostic.
 * - It preserves your existing behavior; do not “fix” it without auditing UI diffs.
 */
fun String.toTitleCase(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - Exactly ONE nutrient row per (foodId, nutrientId) is persisted.
 * - Canonical basis:
 *   - PER_100G when mass-grounded
 *   - PER_100ML when volume-grounded
 *   - USDA_REPORTED_SERVING otherwise
 * - Never infer density; never convert grams <-> mL.
 * - Preserve stableId on revive to avoid breaking log references.
 * - All writes must occur inside a single DB transaction.
 *
 * Do not refactor notes
 * - Keep grounding decision order: mass grounding takes precedence over volume grounding.
 * - Keep revive-on-import logic (unique usdaFdcId constraint safety).
 * - Keep groupBy(nutrient.id).firstOrNull() de-dupe behavior unless you also update DB constraints and readers.
 * - Generic USDA items with missing servingSizeUnit may default to 100 g mass-grounded import.
 *   This is intentional to avoid blocking Foundation/generic foods that are effectively reported on a 100 g basis.
 *
 * Architectural boundaries
 * - This use case is a domain/persistence orchestration boundary:
 *   - parsing + mapping is domain logic
 *   - writes happen through repositories inside a transaction
 *
 * Migration notes (KMP / time APIs)
 * - This file currently depends on android.util.Log; replace with injected logger for KMP.
 *
 * Performance considerations
 * - Conversion is in-memory; DB writes are batched via replaceForFood.
 *
 * Maintenance recommendations
 * - If title casing changes, audit UI snapshots (names/brands will change).
 * - Consider a single shared title-case util if multiple USDA import paths need identical behavior.
 */

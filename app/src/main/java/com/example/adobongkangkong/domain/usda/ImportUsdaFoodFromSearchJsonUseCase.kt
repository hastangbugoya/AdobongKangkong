package com.example.adobongkangkong.domain.usda

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
import com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

/**
 * Imports the first USDA foods/search result from a JSON response string into the app DB.
 *
 * Canonical storage rule:
 * - Store exactly ONE basis row per (foodId, nutrientId).
 * - If serving is mass-backed: store PER_100G only.
 * - If serving is volume-backed: store PER_100ML only.
 * - Otherwise store USDA_REPORTED_SERVING only (raw; not safely scalable).
 *
 * Volume-grounded liquids:
 * - Example label: "1 can (473 mL)" where nutrients are per 473 mL.
 * - Persist:
 *     servingSize = 1
 *     servingUnit = CAN
 *     mlPerServingUnit = 473
 *     nutrient basis = PER_100ML
 * - No grams involved. No density guessing.
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

        val stableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }

        val brand: String? =
            item.brandName?.trim()?.toTitleCase().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val servingSizeUnit = item.servingSizeUnit
        val rawServingUnit: ServingUnit =
            ServingUnit.fromUsda(servingSizeUnit)
                ?: return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")

        val rawServingSize: Double = item.servingSize ?: 1.0

        // Prefer householdServingFullText for the *display* unit (e.g. "1 can"),
        // and use USDA servingSize+unit as the bridge (e.g. 473 ML per 1 can).
        val household = parseHouseholdServing(item.householdServingFullText)

        // LOCKED-IN USDA RULE:
        // If household text is parseable, we store servingSize=1 for display.
        val finalServingSize: Double = if (household != null) 1.0 else rawServingSize
        val finalServingUnit: ServingUnit = household?.unit ?: rawServingUnit

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
            id = 0L,
            name = normalizedName,
            servingSize = finalServingSize,
            servingUnit = finalServingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            stableId = stableId.ifBlank { UUID.randomUUID().toString() },
            brand = brand,
            isRecipe = false,
            isLowSodium = null
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
                        basisGrams = 100.0
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
                        basisGrams = 100.0
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

    /**
     * If USDA raw serving is mass-based and household serving provides a display unit,
     * compute grams per 1 display-unit.
     *
     * Locked-in USDA behavior:
     * - When household text is parseable, we persist servingSize=1 + servingUnit=<parsed unit>
     * - Therefore the bridge must represent grams-per-1 display unit, which is exactly the USDA raw grams.
     *
     * Absolute prohibition:
     * - Never infer grams from mL.
     */
    private fun computeGramsBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isMassUnit()) return null

        // If the display unit itself is a mass unit, we can rely on deterministic conversion;
        // no bridge needed.
        if (displayUnit.isMassUnit()) return null

        val gramsTotal = rawUnit.toGrams(rawSize)?.takeIf { it > 0.0 } ?: return null
        // Since we persist servingSize=1 for parseable household text, do NOT divide by householdSize.
        return gramsTotal
    }

    /**
     * If USDA raw serving is volume-based and household serving provides a display unit,
     * compute mL per 1 display-unit.
     *
     * Locked-in USDA liquids behavior:
     * - USDA servingSizeUnit=MLT + servingSize=X is the *truth* for canonicalization.
     * - householdServingFullText is display only (but if parseable, it wins for display).
     * - When household text is parseable we persist servingSize=1 + servingUnit=<parsed unit>
     *   and preserve the truth via mlPerServingUnit=X.
     *
     * Absolute prohibition:
     * - Never infer mL from grams (no density guessing).
     */
    private fun computeMlBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isVolumeUnit()) return null

        // Even if displayUnit is a volume unit (e.g., CUP/FLOZ), USDA mL is the truth.
        // Preserve the USDA serving mL via the bridge.
        val mlTotal = rawUnit.toMilliliters(rawSize)?.takeIf { it > 0.0 } ?: return null

        // Since we persist servingSize=1 for parseable household text, do NOT divide by householdSize.
        return mlTotal
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

@Serializable
data class UsdaFoodsSearchResponse(
    val totalHits: Int = 0,
    val foods: List<UsdaFoodSearchItem> = emptyList()
)

//@Serializable
//data class UsdaFoodSearchItem(
//    val fdcId: Long,
//    val description: String? = null,
//    val gtinUpc: String? = null,
//    val brandOwner: String? = null,
//    val brandName: String? = null,
//    val ingredients: String? = null,
//    val servingSizeUnit: String? = null,
//    val servingSize: Double? = null,
//    val householdServingFullText: String? = null,
//    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
//)

@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Long,
    val nutrientName: String? = null,
    val unitName: String? = null,
    val value: Double? = null,
    val nutrientNumber: String? = null,
)

fun String.toTitleCase(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }


/**
 * ============================
 * IMPORT_USDA_FOOD_FROM_SEARCH_JSON_USECASE – FUTURE-ME NOTES 1:30pm
 * ============================
 *
 * Purpose:
 * - Take USDA foods/search JSON (already fetched elsewhere), parse it, and import ONE selected food into DB.
 * - Persist:
 *     - Food serving model in FoodEntity (servingSize + servingUnit, plus optional gramsPerServingUnit)
 *     - Nutrients in food_nutrients using the LOCKED canonical basis rule (single basis per nutrient)
 *
 * Critical mindset:
 * - USDA responses can be messy, inconsistent, or branded-data weird.
 * - We do NOT “fix” USDA; we normalize only when mathematically safe.
 * - We do NOT guess density. No mL<->g conversions unless user gives grams-per-serving backing.
 *
 * Canonical storage rule (locked):
 * - Persist exactly ONE row per nutrient per food:
 *     - If USDA serving unit is GRM/G and servingSize > 0 → store PER_100G only (scale from serving to 100g).
 *     - If USDA serving unit is MLT/ML and servingSize > 0 → store PER_100ML only (scale from serving to 100ml).
 *     - Otherwise → store USDA_REPORTED_SERVING only (raw), and the food is BLOCKED until grounded.
 *
 * Why FoodEntity keeps serving info even when nutrients are canonical:
 * - ServingSize/Unit is user-facing and drives UI.
 * - Canonical nutrients are for math and conversions.
 * - Together: you can display “2 Tbsp” but compute using PER_100G if grams backing exists.
 *
 * Nutrient selection nuance:
 * - We only import nutrients that exist in our internal catalog (via UsdaToCsvNutrientMap + NutrientRepository).
 * - If a USDA nutrient is unmapped, skip it.
 * - This keeps dashboards/recipes consistent with app expectations.
 *
 * Traceability nuance:
 * - Food.stableId should be stable (gtin/UPC preferred, else fdcId).
 * - This protects export/import reconciliation and avoids duplicates across syncs.
 *
 * Regression smells:
 * - If you see both USDA_REPORTED_SERVING and PER_100G inserted for the same nutrient → wrong (writer drift).
 * - If UI crashes due to duplicate nutrientId rows → this use case (or CSV importer / save use case) is leaking multi-basis rows.
 *
 * Discipline:
 * - Minimal change policy: do not invent new parsers/IDs.
 * - Use existing ServingUnit.fromUsda + existing BasisType values.
 */

/**
 * AI NOTE (2026-02-06):
 *
 * USDA LIQUIDS TRUTH-vs-DISPLAY (LOCKED-IN):
 * - If householdServingFullText is parseable, we persist servingSize=1 and servingUnit=<parsed unit> for display,
 *   and we preserve USDA truth (servingSizeUnit=MLT + servingSize=X) via mlPerServingUnit=X.
 * - Even if the parsed unit is a volume unit (CUP/FLOZ), mlPerServingUnit MUST win for canonical math.
 *   Never rely on CUP<->mL conversions for USDA truth.
 * - No grams<->mL conversions. No density guessing.
 */

package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import androidx.room.withTransaction
import com.example.adobongkangkong.domain.model.fromUsda

/**
 * Imports the first USDA foods/search result from a JSON response string into the app DB.
 *
 * Canonical storage rule:
 * - Store exactly ONE basis row per (foodId, nutrientId).
 * - If serving is mass-backed: store PER_100G only.
 * - If serving is volume-backed: store PER_100ML only.
 * - Otherwise store USDA_REPORTED_SERVING only (raw; not safely scalable).
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

        val servingUnit = ServingUnit.fromUsda(item.servingSizeUnit)
            ?: return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")

        val servingSize = item.servingSize ?: 1.0

        val gramsPerServingUnit: Double? =
            if (servingUnit == ServingUnit.G) servingSize.takeIf { it > 0.0 } else null

        val stableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }

        val brand: String? =
            item.brandName?.trim().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val food = Food(
            id = 0L,
            name = normalizedName,
            servingSize = servingSize,
            servingUnit = servingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            stableId = stableId.ifBlank { UUID.randomUUID().toString() },
            brand = brand,
            isRecipe = false,
            isLowSodium = null
        )

        val servingRows: List<FoodNutrientRow> = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber]
                ?: return@mapNotNull null

            val nutrient = nutrients.getByCode(csvCode)
                ?: return@mapNotNull null

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
            servingUnit == ServingUnit.G && servingSize > 0.0 -> {
                val factor = 100.0 / servingSize
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100G,
                        amount = r.amount * factor,
                        basisGrams = 100.0
                    )
                }
            }

            servingUnit == ServingUnit.ML && servingSize > 0.0 -> {
                val factor = 100.0 / servingSize
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

        val foodId = db.withTransaction {
            val id = foods.upsert(food)
            foodNutrients.replaceForFood(id, canonicalRows)
            id
        }

        return Result.Success(foodId)
    }

    sealed class Result {
        data class Success(val foodId: Long) : Result()
        data class Blocked(val reason: String) : Result()
    }
}

@Serializable
data class UsdaFoodsSearchResponse(
    val totalHits: Int = 0,
    val foods: List<UsdaFoodSearchItem> = emptyList()
)

@Serializable
data class UsdaFoodSearchItem(
    val fdcId: Long,
    val description: String? = null,
    val gtinUpc: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val ingredients: String? = null,
    val servingSizeUnit: String? = null,
    val servingSize: Double? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)

@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Long,
    val nutrientName: String? = null,
    val unitName: String? = null,
    val value: Double? = null,
    val nutrientNumber: String? = null,
)

//fun NutrientUnit.Companion.fromUsda(unitName: String?): NutrientUnit? {
//    if (unitName.isNullOrBlank()) return null
//    return when (unitName.trim().uppercase()) {
//        "KCAL" -> NutrientUnit.KCAL
//        "G" -> NutrientUnit.G
//        "MG" -> NutrientUnit.MG
//        "UG", "MCG" -> NutrientUnit.UG
//        "IU" -> NutrientUnit.IU
//        else -> null
//    }
//}

fun String.toTitleCase(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
        }

/**
 * ============================
 * IMPORT_USDA_FOOD_FROM_SEARCH_JSON_USECASE – FUTURE-ME NOTES
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

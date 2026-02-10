package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.repository.NutrientRepository
import java.util.UUID

/**
 * Builds a draft (Food + nutrient rows) from a USDA /foods/search JSON string.
 *
 * - No DB writes
 * - Same mapping rules as ImportUsdaFoodFromSearchJsonUseCase
 */
object BuildDraftFromParsedItem {

    data class Draft(
        val food: Food,
        val rows: List<FoodNutrientRow>
    )

    sealed class Result {
        data class Success(val draft: Draft) : Result()
        data class Blocked(val reason: String) : Result()
    }

    suspend fun fromSearchJson(
        searchJson: String,
        nutrients: NutrientRepository,
        selectedFdcId: Long? = null
    ): Result {
        val parsed = UsdaFoodsSearchParser.parse(searchJson)

        val item: UsdaFoodSearchItem = when (selectedFdcId) {
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
        }.ifBlank { UUID.randomUUID().toString() }

        val brand: String? =
            item.brandName?.trim().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val food = Food(
            id = 0L, // draft
            name = normalizedName,
            servingSize = servingSize,
            servingUnit = servingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = null, // ✅ new param; draft builder does not infer/parse volume bridge here
            stableId = stableId,
            brand = brand,
            isRecipe = false,
            isLowSodium = null,
        )

        // Map USDA nutrients → CSV nutrients (USDA_REPORTED_SERVING only)
        val servingRows = mutableListOf<FoodNutrientRow>()
        for (n in item.foodNutrients) {
            val usdaNumber = n.nutrientNumber ?: continue
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: continue

            val nutrient = nutrients.getByCode(csvCode) ?: continue
            val amt = n.value ?: continue

            servingRows += FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        return Result.Success(
            Draft(
                food = food,
                rows = servingRows
            )
        )
    }

    private fun String.toTitleCase(): String =
        lowercase()
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
}

/**
 * AI NOTE — READ BEFORE REFACTORING (2026-02-06)
 *
 * I will see mlPerServingUnit and be tempted to "fill it in" for USDA drafts.
 * Do NOT invent it. This draft builder currently mirrors the *simple* USDA servingSize+unit mapping only.
 *
 * Rules I must not violate:
 * - mlPerServingUnit is a volume bridge for non-mL units (e.g., CAN/BOTTLE). For ServingUnit.ML it is usually null.
 * - Never guess density; never convert grams <-> mL.
 * - If I later extend this draft to parse householdServingFullText ("1 can (473 mL)"),
 *   I must also add the corresponding mlPerServingUnit=473 and switch display unit to CAN—mirroring ImportUsdaFoodFromSearchJsonUseCase.
 */

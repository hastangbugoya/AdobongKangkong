package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.data.usda.UsdaFoodSearchItem
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

        return fromParsedItem(item = item, nutrients = nutrients)
    }

    suspend fun fromParsedItem(
        item: UsdaFoodSearchItem,
        nutrients: NutrientRepository
    ): Result {
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
            stableId = stableId,
            brand = brand,
            isRecipe = false,
            isLowSodium = null
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

        val allRows = buildList {
            addAll(servingRows)

            // Add derived PER_100G only when serving is grams (same rule)
            if (servingUnit == ServingUnit.G && servingSize > 0.0) {
                val factor = 100.0 / servingSize
                addAll(
                    servingRows.map { r ->
                        r.copy(
                            basisType = BasisType.PER_100G,
                            amount = r.amount * factor,
                            basisGrams = 100.0
                        )
                    }
                )
            }
        }

        return Result.Success(Draft(food = food, rows = allRows))
    }
}

package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.transfer.RecipeBundleDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleFoodDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleFoodNutrientBasis
import com.example.adobongkangkong.domain.transfer.RecipeBundleFoodNutrientDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleIngredientDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleRecipeDto
import javax.inject.Inject

/**
 * ExportRecipeBundleUseCase
 *
 * Builds a self-contained RecipeBundleDto for sharing/export.
 *
 * ## Identity rules
 * - Recipe identity = backing recipe food stableId
 * - Ingredient references = food stableId
 *
 * ## Safety rules
 * - Never throws on missing ingredient food
 * - Skips unresolved foods gracefully
 * - Never exposes DB IDs
 */
class ExportRecipeBundleUseCase @Inject constructor(
    private val recipeRepo: RecipeRepository,
    private val foodRepo: FoodRepository,
    private val foodNutrientRepo: FoodNutrientRepository
) {

    suspend operator fun invoke(recipeFoodId: Long): RecipeBundleDto? {
        val header = recipeRepo.getRecipeByFoodId(recipeFoodId)
            ?: return null

        val recipeFood = foodRepo.getById(header.foodId)
            ?: return null

        val ingredients = recipeRepo.getIngredients(header.recipeId)

        val ingredientDtos = ingredients.mapNotNull { line ->
            val food = foodRepo.getById(line.ingredientFoodId) ?: return@mapNotNull null

            RecipeBundleIngredientDto(
                foodStableId = food.stableId,
                ingredientServings = line.ingredientServings
            )
        }

        val foodIds = buildSet {
            add(header.foodId)
            ingredients.forEach { add(it.ingredientFoodId) }
        }

        val foodDtos = mutableListOf<RecipeBundleFoodDto>()

        for (id in foodIds) {
            val food = foodRepo.getById(id) ?: continue
            val nutrientRows = foodNutrientRepo.getForFood(id)

            val canonicalNutrientBasis = when (nutrientRows.firstOrNull()?.basisType) {
                BasisType.PER_100G -> RecipeBundleFoodNutrientBasis.PER_100G
                BasisType.PER_100ML -> RecipeBundleFoodNutrientBasis.PER_100ML
                BasisType.USDA_REPORTED_SERVING, null -> null
            }

            val nutrientDtos = nutrientRows.map { row ->
                RecipeBundleFoodNutrientDto(
                    code = row.nutrient.code,
                    amount = row.amount
                )
            }

            foodDtos.add(
                RecipeBundleFoodDto(
                    stableId = food.stableId,
                    name = food.name,
                    brand = food.brand,
                    servingSize = food.servingSize,
                    servingUnit = food.servingUnit.name,
                    gramsPerServingUnit = food.gramsPerServingUnit,
                    mlPerServingUnit = food.mlPerServingUnit,
                    servingsPerPackage = food.servingsPerPackage,
                    isRecipe = food.isRecipe,
                    isLowSodium = food.isLowSodium,
                    usdaFdcId = food.usdaFdcId,
                    usdaGtinUpc = food.usdaGtinUpc,
                    usdaPublishedDate = food.usdaPublishedDate,
                    usdaModifiedDate = food.usdaModifiedDate,
                    usdaServingSize = food.usdaServingSize,
                    usdaServingUnit = food.usdaServingUnit?.name,
                    householdServingText = food.householdServingText,
                    canonicalNutrientBasis = canonicalNutrientBasis,
                    nutrients = nutrientDtos
                )
            )
        }

        return RecipeBundleDto(
            exportedAtEpochMs = System.currentTimeMillis(),
            recipe = RecipeBundleRecipeDto(
                stableId = recipeFood.stableId,
                name = recipeFood.name,
                servingsYield = header.servingsYield,
                totalYieldGrams = header.totalYieldGrams
            ),
            ingredients = ingredientDtos,
            foods = foodDtos
        )
    }
}
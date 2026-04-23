package com.example.adobongkangkong.domain.transfer

import android.util.Log
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.usecase.CreateRecipeUseCase
import com.example.adobongkangkong.domain.usecase.SaveFoodWithNutrientsUseCase
import javax.inject.Inject

class ImportRecipeBundleUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val nutrientRepository: NutrientRepository,
    private val saveFoodWithNutrients: SaveFoodWithNutrientsUseCase,
    private val createRecipeUseCase: CreateRecipeUseCase
) {

    companion object {
        private const val TAG = "AK_IMPORT"
    }

    sealed class Result {
        data class Success(
            val createdFoodIds: List<Long>,
            val recipeId: Long
        ) : Result()

        data class PartialSuccess(
            val createdFoodIds: List<Long>,
            val recipeId: Long?,
            val unresolvedFoods: List<String>,
            val unresolvedIngredients: List<String>
        ) : Result()

        data class Failure(
            val reason: Reason,
            val message: String? = null
        ) : Result()
    }

    enum class Reason {
        INVALID_INPUT,
        TOO_MANY_MISSING_INGREDIENTS,
        RECIPE_CREATION_FAILED,
        INTERNAL_ERROR
    }

    suspend fun execute(bundle: RecipeBundleDto): Result {
        return try {
            val recipeStableId = bundle.recipe.stableId

            val stableIdToFoodId = mutableMapOf<String, Long>()
            val createdFoodIds = mutableListOf<Long>()
            val unresolvedFoods = mutableListOf<String>()

            // Import only non-recipe foods.
            // The actual recipe backing food is created later by CreateRecipeUseCase.
            for (foodDto in bundle.foods) {
                if (foodDto.stableId == recipeStableId || foodDto.isRecipe) {
                    continue
                }

                val existing = foodRepository.getByStableId(foodDto.stableId)
                if (existing != null) {
                    stableIdToFoodId[foodDto.stableId] = existing.id
                    continue
                }

                val food = mapDtoToFood(foodDto)
                val nutrientRows = mapDtoToNutrientsResolved(foodDto)

                if (food == null) {
                    unresolvedFoods.add(foodDto.stableId)
                    continue
                }

                val newId = saveFoodWithNutrients(food, nutrientRows)
                stableIdToFoodId[foodDto.stableId] = newId
                createdFoodIds.add(newId)
            }

            val resolvedIngredients = mutableListOf<RecipeIngredientDraft>()
            val unresolvedIngredients = mutableListOf<String>()

            for (ingredient in bundle.ingredients) {
                val foodId = stableIdToFoodId[ingredient.foodStableId]
                val servings = ingredient.ingredientServings

                if (foodId == null || servings == null || servings <= 0.0) {
                    unresolvedIngredients.add(ingredient.foodStableId)
                    continue
                }

                resolvedIngredients.add(
                    RecipeIngredientDraft(
                        foodId = foodId,
                        ingredientServings = servings
                    )
                )
            }

            val missingRatio =
                if (bundle.ingredients.isEmpty()) 0.0
                else unresolvedIngredients.size.toDouble() / bundle.ingredients.size

            if (missingRatio > 0.5) {
                return Result.Failure(
                    Reason.TOO_MANY_MISSING_INGREDIENTS,
                    "More than 50% ingredients unresolved"
                )
            }

            val recipeDraft = RecipeDraft(
                name = bundle.recipe.name,
                servingsYield = bundle.recipe.servingsYield,
                totalYieldGrams = bundle.recipe.totalYieldGrams,
                ingredients = resolvedIngredients
            )

            val recipeId = try {
                createRecipeUseCase(recipeDraft)
            } catch (e: Exception) {
                Log.e(TAG, "Recipe creation failed", e)
                null
            }

            if (recipeId == null) {
                return Result.Failure(
                    Reason.RECIPE_CREATION_FAILED,
                    "Failed to create recipe"
                )
            }

            return if (unresolvedFoods.isEmpty() && unresolvedIngredients.isEmpty()) {
                Result.Success(createdFoodIds, recipeId)
            } else {
                Result.PartialSuccess(
                    createdFoodIds = createdFoodIds,
                    recipeId = recipeId,
                    unresolvedFoods = unresolvedFoods,
                    unresolvedIngredients = unresolvedIngredients
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.Failure(Reason.INTERNAL_ERROR, e.message)
        }
    }

    private suspend fun mapDtoToNutrientsResolved(
        dto: RecipeBundleFoodDto
    ): List<FoodNutrientRow> {
        val basis = when (dto.canonicalNutrientBasis) {
            RecipeBundleFoodNutrientBasis.PER_100G -> BasisType.PER_100G
            RecipeBundleFoodNutrientBasis.PER_100ML -> BasisType.PER_100ML
            null -> BasisType.USDA_REPORTED_SERVING
        }

        val basisGrams = when (basis) {
            BasisType.PER_100G -> 100.0
            BasisType.PER_100ML -> 100.0
            BasisType.USDA_REPORTED_SERVING -> null
        }

        val rows = mutableListOf<FoodNutrientRow>()

        for (dtoNutrient in dto.nutrients) {
            val nutrient = nutrientRepository.getByCode(dtoNutrient.code)

            if (nutrient == null) {
                Log.e(TAG, "Unknown nutrient code=${dtoNutrient.code}")
                continue
            }

            rows.add(
                FoodNutrientRow(
                    nutrient = nutrient,
                    amount = dtoNutrient.amount,
                    basisType = basis,
                    basisGrams = basisGrams
                )
            )
        }

        return rows
    }

    private fun mapDtoToFood(dto: RecipeBundleFoodDto): Food? {
        return try {
            Food(
                id = 0L,
                stableId = dto.stableId,
                name = dto.name,
                brand = dto.brand,
                servingSize = dto.servingSize,
                servingUnit = parseServingUnitSafe(dto.servingUnit),
                gramsPerServingUnit = dto.gramsPerServingUnit,
                mlPerServingUnit = dto.mlPerServingUnit,
                servingsPerPackage = dto.servingsPerPackage,
                isRecipe = dto.isRecipe,
                isLowSodium = dto.isLowSodium,
                usdaFdcId = dto.usdaFdcId,
                usdaGtinUpc = dto.usdaGtinUpc,
                usdaPublishedDate = dto.usdaPublishedDate,
                usdaModifiedDate = dto.usdaModifiedDate,
                usdaServingSize = dto.usdaServingSize,
                usdaServingUnit = dto.usdaServingUnit?.let { parseServingUnitSafe(it) },
                householdServingText = dto.householdServingText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Food mapping failed", e)
            null
        }
    }

    private fun parseServingUnitSafe(raw: String?): ServingUnit {
        return try {
            if (raw.isNullOrBlank()) ServingUnit.SERVING
            else ServingUnit.valueOf(raw)
        } catch (_: Exception) {
            ServingUnit.SERVING
        }
    }
}
package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.ServingAmountConverter
import com.example.adobongkangkong.domain.usage.UsageContext

interface RecipeIngredientWriter {
    suspend fun upsert(entity: RecipeIngredientEntity)
}

interface FoodLookupForRecipe {
    suspend fun getFoodById(foodId: Long): FoodSnapshot?
}

data class FoodSnapshot(
    val id: Long,
    val servingUnit: com.example.adobongkangkong.domain.model.ServingUnit,
    val gramsPerServingUnit: Double?
)

class AddRecipeIngredientUseCase(
    private val foodLookup: FoodLookupForRecipe,
    private val writer: RecipeIngredientWriter,
    private val checkFoodUsable: CheckFoodUsableUseCase = CheckFoodUsableUseCase()
) {

    sealed interface Result {
        data object Success : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        recipeId: Long,
        ingredientFoodId: Long,
        amountInput: AmountInput
    ): Result {
        val food = foodLookup.getFoodById(ingredientFoodId)
            ?: return Result.Error("Ingredient food not found")

        // Gate: servings-based usage for volume-ish units missing gramsPerServingUnit is blocked.
        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = food.gramsPerServingUnit,
            amountInput = amountInput,
            context = UsageContext.RECIPE
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        val servings: Double = when (amountInput) {
            is AmountInput.ByServings -> amountInput.servings
            is AmountInput.ByGrams -> {
                val r = ServingAmountConverter.gramsToServings(
                    servingUnit = food.servingUnit,
                    gramsPerServingUnit = food.gramsPerServingUnit,
                    grams = amountInput.grams
                )
                r.getOrElse {
                    return Result.Blocked("Set grams-per-serving before adding grams for this ingredient.")
                }
            }
        }

        writer.upsert(
            RecipeIngredientEntity(
                recipeId = recipeId,
                foodId = ingredientFoodId,
                amountServings = when (amountInput) {
                    is AmountInput.ByServings -> amountInput.servings
                    is AmountInput.ByGrams -> {
                        val r = ServingAmountConverter.gramsToServings(
                            servingUnit = food.servingUnit,
                            gramsPerServingUnit = food.gramsPerServingUnit,
                            grams = amountInput.grams
                        )
                        r.getOrElse {
                            return Result.Blocked("Set grams-per-serving before adding grams for this ingredient.")
                        }
                    }
                },
                amountGrams = when (amountInput) {
                    is AmountInput.ByServings -> null
                    is AmountInput.ByGrams -> amountInput.grams
                }
            )
        )
        return Result.Success
    }
}

package com.example.adobongkangkong.domain.logging

import android.util.Log
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.ComputeRecipeBatchNutritionUseCase
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.nutrition.dividedBy
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.recipes.ComputeLoggedRecipeNutritionUseCase
import com.example.adobongkangkong.domain.recipes.toRecipe
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeDraftLookupRepository
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.UsageContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

class UpdateLogEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
    private val logRepository: LogRepository,
    private val checkFoodUsable: CheckFoodUsableUseCase,
    private val recipeDraftLookup: RecipeDraftLookupRepository,
    private val recipeBatchLookup: RecipeBatchLookupRepository,
    private val computeRecipeBatchNutritionUseCase: ComputeRecipeBatchNutritionUseCase,
    private val computeLoggedRecipeNutrition: ComputeLoggedRecipeNutritionUseCase,
    private val recipeDao: RecipeDao
) {

    enum class NutritionDecision {
        USE_CURRENT,
        KEEP_ORIGINAL
    }

    sealed interface Result {
        data class Success(val id: Long) : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
        data class NutritionChoiceRequired(
            val existingEntry: LogEntry,
            val recomputedNutrients: NutrientMap
        ) : Result
    }

    suspend fun execute(
        logId: Long,
        amountInput: AmountInput,
        mealSlot: MealSlot?,
        nutritionDecision: NutritionDecision? = null
    ): Result {
        val existing = logRepository.getById(logId)
            ?: return Result.Error("Log entry not found")

        val (storedAmount, storedUnit) = toStoredAmountAndUnit(amountInput)

        if (
            existing.amount == storedAmount &&
            existing.unit == storedUnit &&
            existing.mealSlot == mealSlot
        ) {
            return Result.Success(existing.id)
        }

        val stableId = existing.foodStableId
            ?: return Result.Error("Log entry is missing food identity")

        val food = resolveFoodForStableId(stableId)
            ?: return Result.Error("Logged food no longer exists")

        val recomputed = if (!food.isRecipe) {
            recomputeFoodNutrients(
                foodId = food.id,
                amountInput = amountInput
            )
        } else {
            recomputeRecipeNutrients(
                foodId = food.id,
                recipeBatchId = existing.recipeBatchId,
                amountInput = amountInput
            )
        }

        val recomputedNutrients = when (recomputed) {
            is RecomputeResult.Success -> recomputed.nutrients
            is RecomputeResult.Blocked -> return Result.Blocked(recomputed.message)
            is RecomputeResult.Error -> return Result.Error(recomputed.message)
        }

        val finalNutrients = when {
            nutritionDecision == NutritionDecision.USE_CURRENT -> recomputedNutrients
            nutritionDecision == NutritionDecision.KEEP_ORIGINAL -> {
                scaleStoredNutrients(
                    existing = existing,
                    food = food,
                    newAmountInput = amountInput
                )
            }

            materiallyDifferentNormalizedBasis(
                existing = existing,
                current = recomputedNutrients,
                food = food,
                editedAmountInput = amountInput
            ) -> {
                return Result.NutritionChoiceRequired(
                    existingEntry = existing,
                    recomputedNutrients = recomputedNutrients
                )
            }

            else -> recomputedNutrients
        }

        val now = Instant.now()

        val updatedEntry = existing.copy(
            itemName = food.name,
            nutrients = finalNutrients,
            amount = storedAmount,
            unit = storedUnit,
            mealSlot = mealSlot,
            modifiedAt = now
        )

        logRepository.update(updatedEntry)
        return Result.Success(existing.id)
    }

    private suspend fun resolveFoodForStableId(stableId: String): Food? {
        foodRepository.getByStableId(stableId)?.let { return it }

        val recipeId = recipeDao.getIdByStableId(stableId) ?: return null
        val recipe = recipeDao.getById(recipeId) ?: return null

        return foodRepository.getById(recipe.foodId)
    }

    private sealed interface RecomputeResult {
        data class Success(val nutrients: NutrientMap) : RecomputeResult
        data class Blocked(val message: String) : RecomputeResult
        data class Error(val message: String) : RecomputeResult
    }

    private fun toStoredAmountAndUnit(amountInput: AmountInput): Pair<Double, LogUnit> {
        return when (amountInput) {
            is AmountInput.ByServings -> amountInput.servings to LogUnit.SERVING
            is AmountInput.ByGrams -> amountInput.grams to LogUnit.GRAM_COOKED
        }
    }

    private suspend fun recomputeFoodNutrients(
        foodId: Long,
        amountInput: AmountInput
    ): RecomputeResult {
        val food = foodRepository.getById(foodId)
            ?: return RecomputeResult.Error("Food not found")

        val gramsPerServing = food.gramsPerServingResolved()

        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = gramsPerServing,
            mlPerServingUnit = food.mlPerServingUnit,
            amountInput = amountInput,
            context = UsageContext.LOGGING
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return RecomputeResult.Blocked(check.message)
        }

        val snapshot = snapshotRepository.getSnapshot(food.id)
            ?: return RecomputeResult.Error("Nutrition snapshot unavailable")

        val nutrients = snapshot.nutrientsPerGram?.let { perG ->
            val grams = when (amountInput) {
                is AmountInput.ByGrams -> amountInput.grams
                is AmountInput.ByServings -> {
                    val resolved = food.gramsPerServingResolved()
                        ?: return RecomputeResult.Blocked(
                            "Set grams-per-serving before editing by servings."
                        )
                    amountInput.servings * resolved
                }
            }
            perG.scaledBy(grams)
        } ?: snapshot.nutrientsPerMilliliter?.let { perMl ->
            val ml = when (amountInput) {
                is AmountInput.ByServings -> {
                    val mlPerServing = when {
                        food.mlPerServingUnit != null &&
                                food.mlPerServingUnit > 0.0 &&
                                food.servingSize > 0.0 ->
                            food.servingSize * food.mlPerServingUnit

                        food.servingUnit.isVolumeUnit() ->
                            food.servingUnit.toMilliliters(food.servingSize)

                        else -> null
                    } ?: return RecomputeResult.Error(
                        "Food is volume-based but missing mL-per-serving."
                    )
                    amountInput.servings * mlPerServing
                }

                is AmountInput.ByGrams -> {
                    return RecomputeResult.Blocked(
                        "This food is volume-based; edit it by servings."
                    )
                }
            }
            perMl.scaledBy(ml)
        } ?: return RecomputeResult.Error("Food nutrition incomplete")

        return RecomputeResult.Success(nutrients)
    }

    private suspend fun recomputeRecipeNutrients(
        foodId: Long,
        recipeBatchId: Long?,
        amountInput: AmountInput
    ): RecomputeResult {
        val recipe = recipeDao.getByFoodId(foodId)
            ?: recipeDao.getById(foodId)
            ?: return RecomputeResult.Error("Recipe data missing for this item.")

        val baseDraft = recipeDraftLookup.getRecipeDraft(recipe.id)
            ?: return RecomputeResult.Error("Recipe not found")

        val batch = if (recipeBatchId != null) {
            recipeBatchLookup.getBatchById(recipeBatchId)
                ?: return RecomputeResult.Error("Recipe batch not found")
        } else {
            null
        }

        val effectiveDraft = if (batch != null) {
            baseDraft.copy(
                totalYieldGrams = batch.cookedYieldGrams,
                servingsYield = batch.servingsYieldUsed ?: baseDraft.servingsYield
            )
        } else {
            baseDraft
        }

        val computed = computeRecipeBatchNutritionUseCase(effectiveDraft.toRecipe())
            ?: return RecomputeResult.Error("Recipe nutrition unavailable")

        val logged = computeLoggedRecipeNutrition.invoke(
            recipeNutrition = computed,
            input = amountInput.toRecipeLogInput()
        )

        if (!logged.isAllowed) {
            val reason = logged.warnings.firstOrNull()?.toString()
                ?: "Editing blocked by recipe rules."
            return RecomputeResult.Blocked(reason)
        }

        return RecomputeResult.Success(logged.totals)
    }

    private fun scaleStoredNutrients(
        existing: LogEntry,
        food: Food,
        newAmountInput: AmountInput
    ): NutrientMap {
        val oldCanonicalAmount = when (existing.unit) {
            LogUnit.GRAM_COOKED -> existing.amount
            LogUnit.SERVING,
            LogUnit.ITEM -> {
                val gramsPerServing = food.gramsPerServingResolved()
                if (gramsPerServing != null && gramsPerServing > 0.0) {
                    existing.amount * gramsPerServing
                } else {
                    existing.amount
                }
            }
        }

        val newCanonicalAmount = when (newAmountInput) {
            is AmountInput.ByGrams -> newAmountInput.grams
            is AmountInput.ByServings -> {
                val gramsPerServing = food.gramsPerServingResolved()
                if (gramsPerServing != null && gramsPerServing > 0.0) {
                    newAmountInput.servings * gramsPerServing
                } else {
                    newAmountInput.servings
                }
            }
        }

        val safeOldAmount = oldCanonicalAmount.takeIf { it > 0.0 } ?: 1.0
        val factor = newCanonicalAmount / safeOldAmount
        return existing.nutrients.scaledBy(factor)
    }

    private fun materiallyDifferentNormalizedBasis(
        existing: LogEntry,
        current: NutrientMap,
        food: Food,
        editedAmountInput: AmountInput,
        absoluteTolerance: Double = 0.05,
        relativeTolerance: Double = 0.05
    ): Boolean {
        val oldCanonicalAmount = canonicalAmountForStoredLog(
            existing = existing,
            food = food
        ) ?: return false

        val newCanonicalAmount = canonicalAmountForEditedInput(
            amountInput = editedAmountInput,
            food = food
        ) ?: return false

        if (oldCanonicalAmount <= 0.0 || newCanonicalAmount <= 0.0) {
            return false
        }

        val oldBasis = existing.nutrients.dividedBy(oldCanonicalAmount)
        val currentBasis = current.dividedBy(newCanonicalAmount)

        return SENTINEL_NUTRIENTS.any { key ->
            val oldValue = oldBasis[key]
            val currentValue = currentBasis[key]

            val diff = abs(oldValue - currentValue)
            val safeOld = if (abs(oldValue) < 1e-6) 0.0 else oldValue
            val safeCurrent = if (abs(currentValue) < 1e-6) 0.0 else currentValue
            val scale = maxOf(abs(safeOld), abs(safeCurrent), 1.0)

            diff > absoluteTolerance && (diff / scale) > relativeTolerance
        }
    }

    private fun canonicalAmountForStoredLog(
        existing: LogEntry,
        food: Food
    ): Double? {
        return when (existing.unit) {
            LogUnit.GRAM_COOKED -> existing.amount.takeIf { it > 0.0 }

            LogUnit.SERVING,
            LogUnit.ITEM -> {
                val gramsPerServing = when {
                    existing.gramsPerServingCooked != null &&
                            existing.gramsPerServingCooked > 0.0 ->
                        existing.gramsPerServingCooked

                    else -> food.gramsPerServingResolved()
                }

                if (gramsPerServing != null && gramsPerServing > 0.0) {
                    existing.amount * gramsPerServing
                } else {
                    existing.amount.takeIf { it > 0.0 }
                }
            }
        }
    }

    private fun canonicalAmountForEditedInput(
        amountInput: AmountInput,
        food: Food
    ): Double? {
        return when (amountInput) {
            is AmountInput.ByGrams -> amountInput.grams.takeIf { it > 0.0 }

            is AmountInput.ByServings -> {
                val gramsPerServing = food.gramsPerServingResolved()
                if (gramsPerServing != null && gramsPerServing > 0.0) {
                    amountInput.servings * gramsPerServing
                } else {
                    amountInput.servings.takeIf { it > 0.0 }
                }
            }
        }
    }

    private companion object {
        val SENTINEL_NUTRIENTS = listOf(
            NutrientKey.CALORIES_KCAL,
            NutrientKey.PROTEIN_G,
            NutrientKey.CARBS_G,
            NutrientKey.FAT_G,
            NutrientKey.SODIUM_MG
        )
    }
}
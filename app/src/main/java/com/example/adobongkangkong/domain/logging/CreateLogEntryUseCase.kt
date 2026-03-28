package com.example.adobongkangkong.domain.logging

import android.util.Log
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.model.gPerUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.ComputeRecipeBatchNutritionUseCase
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
import java.util.UUID
import javax.inject.Inject

// NOTE:
// Recipe logging intentionally bypasses RecipeDraft editing state coming from UI.
// We load the persisted recipe draft from DB so recipe-estimate logs and batch-backed
// logs are derived from a stable persisted recipe definition.
//
// Logging concept:
// - FoodRef.Food   -> direct food log
// - FoodRef.Recipe -> recipe-estimate log when recipeBatchId == null
// - FoodRef.Recipe -> batch-anchored recipe log when recipeBatchId != null
class CreateLogEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
    private val logRepository: LogRepository,
    private val checkFoodUsable: CheckFoodUsableUseCase,
    private val recipeDraftLookup: RecipeDraftLookupRepository,
    private val recipeBatchLookup: RecipeBatchLookupRepository,
    private val computeRecipeBatchNutritionUseCase: ComputeRecipeBatchNutritionUseCase,
    private val computeLoggedRecipeNutrition: ComputeLoggedRecipeNutritionUseCase
) {

    sealed interface Result {
        data class Success(val id: Long) : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        ref: FoodRef,
        timestamp: Instant,
        amountInput: AmountInput,
        recipeBatchId: Long? = null,
        overrideGramsPerServingUnit: Double? = null,
        mealSlot: MealSlot? = null,
        logDateIso: String,
    ): Result {
        return when (ref) {
            is FoodRef.Food -> logFood(
                foodId = ref.foodId,
                timestamp = timestamp,
                amountInput = amountInput,
                overrideGramsPerServingUnit = overrideGramsPerServingUnit,
                mealSlot = mealSlot,
                logDateIso = logDateIso
            )

            is FoodRef.Recipe -> logRecipe(
                recipeRef = ref,
                recipeBatchId = recipeBatchId,
                timestamp = timestamp,
                amountInput = amountInput,
                mealSlot = mealSlot,
                logDateIso = logDateIso
            )
        }
    }

    private fun toStoredAmountAndUnit(amountInput: AmountInput): Pair<Double, LogUnit> {
        return when (amountInput) {
            is AmountInput.ByServings -> amountInput.servings to LogUnit.SERVING
            is AmountInput.ByGrams -> amountInput.grams to LogUnit.GRAM_COOKED
        }
    }

    private suspend fun logFood(
        foodId: Long,
        timestamp: Instant,
        amountInput: AmountInput,
        overrideGramsPerServingUnit: Double? = null,
        mealSlot: MealSlot?,
        logDateIso: String
    ): Result {
        val food = foodRepository.getById(foodId)
            ?: return Result.Error("Food not found")

        val gramsPerServing: Double? = computeGramsPerServing(
            food = food,
            overrideGramsPerServing = overrideGramsPerServingUnit
        )

        val mlPerServing: Double? = computeMlPerServing(food)

        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = gramsPerServing,
            mlPerServingUnit = mlPerServing,
            amountInput = amountInput,
            context = UsageContext.LOGGING
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        val snapshot = snapshotRepository.getSnapshot(food.id)
            ?: return Result.Error("Nutrition snapshot unavailable")

        val nutrients = snapshot.nutrientsPerGram?.let { perG ->
            val grams = when (amountInput) {
                is AmountInput.ByGrams -> amountInput.grams
                is AmountInput.ByServings -> {
                    val resolvedGramsPerServing = gramsPerServing
                        ?: return Result.Blocked("Set grams-per-serving before logging by servings.")
                    amountInput.servings * resolvedGramsPerServing
                }
            }

            perG.scaledBy(grams)
        } ?: snapshot.nutrientsPerMilliliter?.let { perMl ->
            val ml = when (amountInput) {
                is AmountInput.ByServings -> {
                    val resolvedMlPerServing = mlPerServing ?: return Result.Error(
                        "Food is volume-based but missing mL-per-serving (cannot scale)."
                    )
                    amountInput.servings * resolvedMlPerServing
                }

                is AmountInput.ByGrams -> {
                    return Result.Blocked("This food is volume-based; log it by servings.")
                }
            }

            perMl.scaledBy(ml)
        } ?: return Result.Error("Food nutrition incomplete")

        val (storedAmount, storedUnit) = toStoredAmountAndUnit(amountInput)

        val now = Instant.now()

        val entry = LogEntry(
            stableId = UUID.randomUUID().toString(),
            createdAt = now,
            modifiedAt = now,
            timestamp = timestamp,
            foodStableId = food.stableId,
            itemName = food.name,
            nutrients = nutrients,
            amount = storedAmount,
            unit = storedUnit,
            mealSlot = mealSlot,
            logDateIso = logDateIso
        )

        val id = logRepository.insert(entry)
        return Result.Success(id = id)
    }

    private suspend fun logRecipe(
        recipeRef: FoodRef.Recipe,
        timestamp: Instant,
        amountInput: AmountInput,
        recipeBatchId: Long?,
        mealSlot: MealSlot?,
        logDateIso: String
    ): Result {
        val baseDraft = recipeDraftLookup.getRecipeDraft(recipeRef.recipeId)
            ?: return Result.Error("Recipe not found")

        val batch = if (recipeBatchId != null) {
            val resolvedBatch = recipeBatchLookup.getBatchById(recipeBatchId)
                ?: return Result.Error("Recipe batch not found")

            if (resolvedBatch.recipeId != recipeRef.recipeId) {
                return Result.Blocked("Selected batch does not belong to this recipe.")
            }

            resolvedBatch
        } else null

        val effectiveDraft = if (batch != null) {
            baseDraft.copy(
                totalYieldGrams = batch.cookedYieldGrams,
                servingsYield = batch.servingsYieldUsed ?: baseDraft.servingsYield
            )
        } else baseDraft

        val computed = computeRecipeBatchNutritionUseCase(effectiveDraft.toRecipe())
            ?: return Result.Error("Recipe nutrition unavailable")

        val logged = computeLoggedRecipeNutrition.invoke(
            recipeNutrition = computed,
            input = amountInput.toRecipeLogInput()
        )

        if (!logged.isAllowed) {
            val reason = logged.warnings.firstOrNull()?.toString()
                ?: "Logging blocked by recipe rules."
            return Result.Blocked(reason)
        }

        val gramsPerServingCooked = if (batch != null) {
            batch.gramsPerServingCooked(
                fallbackServings = effectiveDraft.servingsYield
            )
        } else null

        val (storedAmount, storedUnit) = toStoredAmountAndUnit(amountInput)

        val now = Instant.now()

        val entry = LogEntry(
            stableId = UUID.randomUUID().toString(),
            createdAt = now,
            modifiedAt = now,
            timestamp = timestamp,
            foodStableId = recipeRef.stableId,
            itemName = recipeRef.displayName,
            nutrients = logged.totals,
            amount = storedAmount,
            unit = storedUnit,
            recipeBatchId = batch?.batchId,
            gramsPerServingCooked = gramsPerServingCooked,
            mealSlot = mealSlot,
            logDateIso = logDateIso
        )

        val id = logRepository.insert(entry)
        return Result.Success(id = id)
    }

    private fun computeGramsPerServing(
        food: com.example.adobongkangkong.domain.model.Food,
        overrideGramsPerServing: Double? = null
    ): Double? {
        overrideGramsPerServing?.takeIf { it > 0.0 }?.let { return it }

        val gramsPerUnitFromUnit = runCatching { food.servingUnit.gPerUnit() }.getOrNull()
        if (gramsPerUnitFromUnit != null && gramsPerUnitFromUnit > 0.0) {
            val size = food.servingSize
            if (size > 0.0) return size * gramsPerUnitFromUnit
        }

        val gramsPerUnitFromBridge = food.gramsPerServingUnit?.takeIf { it > 0.0 } ?: return null
        val size = food.servingSize
        if (size <= 0.0) return null

        return size * gramsPerUnitFromBridge
    }

    private fun computeMlPerServing(
        food: com.example.adobongkangkong.domain.model.Food
    ): Double? {
        val mlPerUnitFromUnit = if (food.servingUnit.isVolumeUnit()) {
            food.servingUnit.toMilliliters(1.0)
        } else {
            null
        }

        if (mlPerUnitFromUnit != null && mlPerUnitFromUnit > 0.0) {
            val size = food.servingSize
            if (size > 0.0) return size * mlPerUnitFromUnit
        }

        val mlPerUnitFromBridge = food.mlPerServingUnit?.takeIf { it > 0.0 } ?: return null
        val size = food.servingSize
        if (size <= 0.0) return null

        return size * mlPerUnitFromBridge
    }
}
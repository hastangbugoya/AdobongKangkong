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

        // Interpret gramsPerServingUnit consistently as "grams per 1 servingUnit".
        // Therefore grams-per-1-serving = servingSize * gramsPerServingUnit.
        val gramsPerServing: Double? = run {
            // If override is provided, it is interpreted as grams-per-1-serving.
            overrideGramsPerServingUnit?.takeIf { it > 0.0 }?.let { return@run it }

            // If servingUnit itself is mass, servingSize is already the mass per serving.
            runCatching { food.servingUnit.gPerUnit() }.getOrNull()?.let { gPerUnit ->
                val size = food.servingSize
                if (size > 0.0) return@run size * gPerUnit
            }

            // Otherwise gramsPerServingUnit is grams per 1 servingUnit (e.g. g per TBSP).
            val gPerUnit = food.gramsPerServingUnit?.takeIf { it > 0.0 } ?: return@run null
            val size = food.servingSize
            if (size > 0.0) size * gPerUnit else null
        }

        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = gramsPerServing,
            mlPerServingUnit = food.mlPerServingUnit,
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
                    val resolvedGramsPerServing = food.gramsPerServingResolved()
                        ?: return Result.Blocked("Set grams-per-serving before logging by servings.")
                    amountInput.servings * resolvedGramsPerServing
                }
            }

            Log.d(
                "Meow",
                "CreateLogEntryUseCase> scaleByGrams grams=$grams gramsPerServing=$gramsPerServing amountInput=$amountInput"
            )

            perG.scaledBy(grams)
        } ?: snapshot.nutrientsPerMilliliter?.let { perMl ->
            val ml = when (amountInput) {
                is AmountInput.ByServings -> {
                    val mlPerServing: Double? = run {
                        food.mlPerServingUnit?.takeIf { it > 0.0 }?.let { mlPerUnit ->
                            if (food.servingSize > 0.0) return@run food.servingSize * mlPerUnit
                        }

                        if (food.servingUnit.isVolumeUnit()) {
                            return@run food.servingUnit.toMilliliters(food.servingSize)
                        }

                        null
                    }

                    val resolvedMlPerServing = mlPerServing ?: return Result.Error(
                        "Food is volume-based but missing mL-per-serving (cannot scale)."
                    )
                    amountInput.servings * resolvedMlPerServing
                }

                is AmountInput.ByGrams -> {
                    return Result.Blocked("This food is volume-based; log it by servings.")
                }
            }

            Log.d(
                "Meow",
                "CreateLogEntryUseCase> scaleByMl ml=$ml mlPerServingUnit=${food.mlPerServingUnit} amountInput=$amountInput"
            )

            perMl.scaledBy(ml)
        } ?: return Result.Error("Food nutrition incomplete")

        Log.d(
            "Meow",
            "CreateLogEntryUseCase> Snapshot debug foodId=${food.id} " +
                    "serving=${food.servingSize} ${food.servingUnit} " +
                    "gramsPerServing=$gramsPerServing " +
                    "food.gpsu=${food.gramsPerServingUnit} food.mlpsu=${food.mlPerServingUnit} " +
                    "snapshot.hasNutrientsPerGram=${snapshot.nutrientsPerGram != null} " +
                    "snapshot.hasNutrientsPerMilliliter=${snapshot.nutrientsPerMilliliter != null}"
        )

        val (storedAmount, storedUnit) = toStoredAmountAndUnit(amountInput)

        val entry = LogEntry(
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
        } else {
            null
        }

        // No batch => recipe-estimate logging using persisted recipe definition.
        // Batch present => apply cooked-yield / servings-yield context from that exact batch.
        val effectiveDraft = if (batch != null) {
            baseDraft.copy(
                totalYieldGrams = batch.cookedYieldGrams,
                servingsYield = batch.servingsYieldUsed ?: baseDraft.servingsYield
            )
        } else {
            baseDraft
        }

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
        } else {
            null
        }

        val (storedAmount, storedUnit) = toStoredAmountAndUnit(amountInput)

        val entry = LogEntry(
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
}
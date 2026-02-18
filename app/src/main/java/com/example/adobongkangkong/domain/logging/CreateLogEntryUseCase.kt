package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.gPerUnit
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
import javax.inject.Inject
// NOTE:
// Recipe logging intentionally bypasses RecipeDraft.
// We always load persisted RecipeEntity + ingredients from DB
// to ensure correct identity and reproducible nutrition snapshots.
class CreateLogEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
    private val logRepository: LogRepository,
    private val checkFoodUsable: CheckFoodUsableUseCase,

    // NEW: recipe batch context + snapshot
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
        overrideGramsPerServingUnit: Double? = null
    ): Result {
        return when (ref) {
            is FoodRef.Food -> logFood(
                foodId = ref.foodId,
                timestamp = timestamp,
                amountInput = amountInput,
                overrideGramsPerServingUnit = overrideGramsPerServingUnit
            )

            is FoodRef.Recipe -> logRecipe(
                recipeRef = ref,
                recipeBatchId = recipeBatchId,
                timestamp = timestamp,
                amountInput = amountInput
            )
        }
    }

    private suspend fun logFood(
        foodId: Long,
        timestamp: Instant,
        amountInput: AmountInput,
        overrideGramsPerServingUnit: Double? = null
    ): Result {
        val food = foodRepository.getById(foodId)
            ?: return Result.Error("Food not found")

        // ✅ Resolve grams-per-serving if possible:
        // - If user already set gramsPerServingUnit, use it.
        // - Else if the serving unit itself is a mass unit (g/mg/kg/oz/lb), derive it from servingSize.
        val resolvedGramsPerServingUnit: Double? =
            overrideGramsPerServingUnit
                ?: food.gramsPerServingUnit
                ?: food.servingUnit.gPerUnit()?.let { gPerUnit ->
                    val size = food.servingSize
                    if (size > 0.0) size * gPerUnit else null
                }
        // Enforce logging rules (volume → grams-per-serving)
        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = resolvedGramsPerServingUnit, // ✅ use resolved
            amountInput = amountInput,
            context = UsageContext.LOGGING
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        val grams = when (amountInput) {
            is AmountInput.ByGrams -> amountInput.grams

            is AmountInput.ByServings -> {
                val gpsu = resolvedGramsPerServingUnit
                    ?: return Result.Blocked("Set grams-per-serving before logging by servings.")
                amountInput.servings * gpsu
            }
        }

        val snapshot = snapshotRepository.getSnapshot(food.id)
            ?: return Result.Error("Nutrition snapshot unavailable")

        val nutrients = snapshot.nutrientsPerGram
            ?.scaledBy(grams)
            ?: return Result.Error("Food nutrition incomplete")

        val entry = LogEntry(
            timestamp = timestamp,
            foodStableId = food.stableId,
            itemName = food.name,
            nutrients = nutrients
        )

        val id = logRepository.insert(entry)
        return Result.Success(id = id)
    }

    private suspend fun logRecipe(
        recipeRef: FoodRef.Recipe,
        timestamp: Instant,
        amountInput: AmountInput,
        recipeBatchId: Long?
    ): Result {
        val batchId = recipeBatchId
            ?: return Result.Blocked("Select or create a cooked batch first.")

        val batch = recipeBatchLookup.getBatchById(batchId)
            ?: return Result.Error("Recipe batch not found")

        if (batch.recipeId != recipeRef.recipeId) {
            return Result.Blocked("Selected batch does not belong to this recipe.")
        }

        val baseDraft = recipeDraftLookup.getRecipeDraft(recipeRef.recipeId)
            ?: return Result.Error("Recipe not found")

        // Apply batch context (this is the *whole point* of the batch)
        val effectiveDraft = baseDraft.copy(
            totalYieldGrams = batch.cookedYieldGrams,
            servingsYield = batch.servingsYieldUsed ?: baseDraft.servingsYield
        )

        val computed = computeRecipeBatchNutritionUseCase(effectiveDraft.toRecipe())
            ?: return Result.Error("Recipe nutrition unavailable")

        val logged = computeLoggedRecipeNutrition.invoke(
            recipeNutrition = computed,
            input = amountInput.toRecipeLogInput()
        )

        if (!logged.isAllowed) {
            // Prefer your existing warning/message string if present
            val reason = logged.warnings.firstOrNull()?.toString()
                ?: "Logging blocked by recipe rules."
            return Result.Blocked(reason ?: "Logging blocked by recipe rules.")
        }

        val entry = LogEntry(
            timestamp = timestamp,
            foodStableId = recipeRef.stableId,
            itemName = recipeRef.displayName,
            nutrients = logged.totals
        )

        val id = logRepository.insert(entry)
        return Result.Success(id = id)
    }
}

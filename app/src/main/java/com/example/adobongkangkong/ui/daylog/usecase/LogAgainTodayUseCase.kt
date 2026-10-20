package com.example.adobongkangkong.ui.daylog.usecase

import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Creates a new log entry for today from an existing historical Day Log row.
 *
 * Behavior:
 * - This is not a DB-row clone.
 * - It reconstructs a fresh log using the original row's amount semantics.
 * - It uses the app's current food / recipe nutrition logic.
 * - The new entry is dated for "today" and timestamped "now".
 *
 * Historical recipe identity compatibility (AK-18):
 * - Some recipe logs store RecipeEntity.stableId in foodStableId.
 * - If direct food resolution fails, the recipe stable ID is
 *   resolved to its backing FoodEntity.
 * - Deleted recipes remain ineligible for re-logging.
 * - Historical log snapshots are never modified.
 * - Recipe variants are re-logged using their original recipeVariantId.
 * - Variant ownership is validated against the resolved base recipe.
 * - Variant re-logging delegates to FoodRef.RecipeVariant, preserving
 *   variant identity and recalculating nutrients from the current definition.
 * - Re-logging never modifies the original historical snapshot.
 *
 */


class LogAgainTodayUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val recipeVariantRepository: RecipeVariantRepository,
    private val recipeDao: RecipeDao,
    private val recipeBatchLookupRepository: RecipeBatchLookupRepository,
    private val createLogEntryUseCase: CreateLogEntryUseCase,
    private val zoneId: ZoneId
) {

    sealed interface Result {
        data class Success(val id: Long) : Result
        data class ConfirmationRequired(val message: String) : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        logId: Long,
        allowBatch: Boolean
    ): Result {
        val source = logRepository.getById(logId)
            ?: return Result.Error("Log entry not found.")

        val stableId = source.foodStableId
            ?.takeIf { it.isNotBlank() }
            ?: return Result.Blocked("This log entry is missing food identity.")

        val amountInput = source.toAmountInput()

        val today = LocalDate.now(zoneId).toString()
        val now = Instant.now()

        val resolvedFood = resolveCurrentFood(stableId)
            ?: return Result.Blocked("Logged food no longer exists.")

        return if (resolvedFood.isRecipe) {
            if (source.recipeVariantId != null) {
                logRecipeVariantAgainToday(
                    sourceLog = source,
                    resolvedRecipeFood = resolvedFood,
                    amountInput = amountInput,
                    now = now,
                    todayIso = today
                )
            } else {
                logRecipeAgainToday(
                    sourceLog = source,
                    resolvedRecipeFood = resolvedFood,
                    amountInput = amountInput,
                    now = now,
                    todayIso = today,
                    allowBatch = allowBatch
                )
            }
        } else {
            logFoodAgainToday(
                resolvedFood = resolvedFood,
                amountInput = amountInput,
                now = now,
                todayIso = today,
                mealSlot = source.mealSlot
            )
        }
    }

    private suspend fun logFoodAgainToday(
        resolvedFood: Food,
        amountInput: AmountInput,
        now: Instant,
        todayIso: String,
        mealSlot: com.example.adobongkangkong.data.local.db.entity.MealSlot?
    ): Result {
        return when (
            val result = createLogEntryUseCase.execute(
                ref = FoodRef.Food(foodId = resolvedFood.id),
                timestamp = now,
                amountInput = amountInput,
                recipeBatchId = null,
                mealSlot = mealSlot,
                logDateIso = todayIso
            )
        ) {
            is CreateLogEntryUseCase.Result.Success -> Result.Success(result.id)
            is CreateLogEntryUseCase.Result.Blocked -> Result.Blocked(result.message)
            is CreateLogEntryUseCase.Result.Error -> Result.Error(result.message)
        }
    }

    private suspend fun logRecipeAgainToday(
        sourceLog: com.example.adobongkangkong.domain.model.LogEntry,
        resolvedRecipeFood: Food,
        amountInput: AmountInput,
        now: Instant,
        todayIso: String,
        allowBatch: Boolean
    ): Result {
        val recipeHeader = recipeRepository.getRecipeByFoodId(resolvedRecipeFood.id)
            ?: return Result.Blocked("Recipe data is missing for this item.")

        val recipeBatchId = sourceLog.recipeBatchId

        if (recipeBatchId != null && !allowBatch) {
            return Result.ConfirmationRequired(
                "This log used a specific recipe batch. Log again today using that batch?"
            )
        }

        if (recipeBatchId != null) {
            val batch = recipeBatchLookupRepository.getBatchById(recipeBatchId)
                ?: return Result.Blocked("The original recipe batch is no longer available.")

            if (batch.recipeId != recipeHeader.recipeId) {
                return Result.Blocked("The original recipe batch no longer matches this recipe.")
            }
        }

        return when (
            val result = createLogEntryUseCase.execute(
                ref = FoodRef.Recipe(
                    recipeId = recipeHeader.recipeId,
                    stableId = resolvedRecipeFood.stableId,
                    displayName = resolvedRecipeFood.name,
                    servingsYieldDefault = recipeHeader.servingsYield
                ),
                timestamp = now,
                amountInput = amountInput,
                recipeBatchId = recipeBatchId,
                mealSlot = sourceLog.mealSlot,
                logDateIso = todayIso
            )
        ) {
            is CreateLogEntryUseCase.Result.Success -> Result.Success(result.id)
            is CreateLogEntryUseCase.Result.Blocked -> Result.Blocked(result.message)
            is CreateLogEntryUseCase.Result.Error -> Result.Error(result.message)
        }
    }


    /**
     * AK-18 addendum: Re-logs a historical recipe variant using its
     * original variant identity and the current variant definition.
     *
     * The original log's nutrient snapshot remains unchanged.
     * The new log receives a fresh snapshot from CreateLogEntryUseCase.
     *
     * The variant must still exist and belong to the resolved base recipe.
     */
    private suspend fun logRecipeVariantAgainToday(
        sourceLog: com.example.adobongkangkong.domain.model.LogEntry,
        resolvedRecipeFood: Food,
        amountInput: AmountInput,
        now: Instant,
        todayIso: String
    ): Result {
        val variantId = sourceLog.recipeVariantId
            ?: return Result.Blocked("Recipe variant identity is missing.")

        val recipeHeader = recipeRepository.getRecipeByFoodId(
            resolvedRecipeFood.id
        ) ?: return Result.Blocked(
            "Recipe data is missing for this item."
        )

        val variant = recipeVariantRepository.getVariantById(variantId)
            ?: return Result.Blocked(
                "The original recipe variant no longer exists."
            )

        if (variant.recipeFoodId != resolvedRecipeFood.id) {
            return Result.Blocked(
                "The original recipe variant no longer belongs to this recipe."
            )
        }

        val recipe = recipeDao.getById(recipeHeader.recipeId)
            ?: return Result.Blocked(
                "Recipe data is missing for this item."
            )

        if (recipe.isDeleted) {
            return Result.Blocked("Recipe no longer exists.")
        }

        return when (
            val result = createLogEntryUseCase.execute(
                ref = FoodRef.RecipeVariant(
                    recipeId = recipeHeader.recipeId,
                    variantId = variant.id,
                    stableId = recipe.stableId,
                    displayName = "${resolvedRecipeFood.name} • ${variant.name}",
                    servingsYieldDefault = variant.servingsYieldOverride
                        ?: recipeHeader.servingsYield
                ),
                timestamp = now,
                amountInput = amountInput,
                recipeBatchId = null,
                mealSlot = sourceLog.mealSlot,
                logDateIso = todayIso
            )
        ) {
            is CreateLogEntryUseCase.Result.Success ->
                Result.Success(result.id)

            is CreateLogEntryUseCase.Result.Blocked ->
                Result.Blocked(result.message)

            is CreateLogEntryUseCase.Result.Error ->
                Result.Error(result.message)
        }
    }


    private suspend fun resolveCurrentFood(stableId: String): Food? {
        // First try the ordinary food identity.
        val direct = foodRepository.getByStableId(stableId)
        if (direct != null) {
            return resolveCanonicalFoodIfMerged(direct)
        }

        // Historical recipe logs may store RecipeEntity.stableId
        // instead of the backing FoodEntity.stableId.
        val recipeId = recipeDao.getIdByStableId(stableId)
            ?: return null

        val recipe = recipeDao.getById(recipeId)
            ?: return null

        // A deleted recipe must not be re-logged.
        if (recipe.isDeleted) return null

        val recipeFood = foodRepository.getById(recipe.foodId)
            ?: return null

        return resolveCanonicalFoodIfMerged(recipeFood)
    }

    private suspend fun resolveCanonicalFoodIfMerged(food: Food): Food? {
        var current: Food? = food
        var guard = 0

        while (current != null && guard < 8) {
            val mergedIntoFoodId = current.mergedIntoFoodId
            if (mergedIntoFoodId == null) {
                return if (current.isDeleted) null else current
            }

            val mergedTarget = foodRepository.getById(mergedIntoFoodId) ?: return null
            current = mergedTarget
            guard++
        }

        return null
    }

    private fun com.example.adobongkangkong.domain.model.LogEntry.toAmountInput(): AmountInput {
        return when (unit) {
            LogUnit.GRAM_COOKED -> AmountInput.ByGrams(amount)
            LogUnit.SERVING,
            LogUnit.ITEM -> AmountInput.ByServings(amount)
        }
    }
}

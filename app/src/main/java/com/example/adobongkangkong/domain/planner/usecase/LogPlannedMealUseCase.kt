package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Logs every item in a Planned Meal into immutable snapshot log entries.
 *
 * ## Duplicate logging prevention
 * A planned meal occurrence can only be logged once. This use case is the correct place to enforce
 * that rule because it logs a whole meal by iterating over its items.
 *
 * Implementation detail:
 * - Atomically marks the planned meal as logged (sets `loggedAtEpochMs`) BEFORE inserting any
 *   log entries.
 * - If the meal is already logged, returns immediately without logging any items.
 *
 * This avoids the bug where guarding inside [CreateLogEntryUseCase] would block item #2+ because
 * [CreateLogEntryUseCase] is called per-item.
 */
class LogPlannedMealUseCase @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val foods: FoodRepository,
    private val recipes: RecipeRepository,
    private val recipeBatches: RecipeBatchLookupRepository,
    private val createLogEntry: CreateLogEntryUseCase
) {

    data class ItemOutcome(
        val plannedItemId: Long,
        val status: Status,
        val message: String? = null
    ) {
        enum class Status { LOGGED, BLOCKED, ERROR, SKIPPED }
    }

    data class Result(
        val loggedCount: Int,
        val blockedCount: Int,
        val errorCount: Int,
        val outcomes: List<ItemOutcome>
    )

    /**
     * Logs the planned meal items for the given [mealId].
     *
     * @param mealId Planned meal occurrence id.
     * @param timestamp Timestamp to store on each resulting log entry (posterity / ordering).
     * @param logDateIso ISO day string (yyyy-MM-dd) to store on each log entry for day membership.
     * @param mealSlot Optional slot override (breakfast/lunch/dinner/etc).
     *
     * @return Per-item outcomes and counts. If the meal is already logged, returns immediately with
     * a single ERROR outcome and no per-item logging.
     */
    suspend fun execute(
        mealId: Long,
        timestamp: Instant,
        logDateIso: String,
        mealSlot: MealSlot? = null
    ): Result {

        // ---- Duplicate logging guard (atomic) ----
        val nowEpochMs = System.currentTimeMillis()
        val marked = plannedMeals.markLoggedIfNotYet(
            plannedMealId = mealId,
            loggedAtEpochMs = nowEpochMs
        )
        if (!marked) {
            // Already logged: do not log items again.
            return Result(
                loggedCount = 0,
                blockedCount = 0,
                errorCount = 1,
                outcomes = listOf(
                    ItemOutcome(
                        plannedItemId = mealId,
                        status = ItemOutcome.Status.ERROR,
                        message = "This planned meal has already been logged."
                    )
                )
            )
        }

        val items = plannedItems.getItemsForMeal(mealId)

        var logged = 0
        var blocked = 0
        var errored = 0

        val outcomes = ArrayList<ItemOutcome>(items.size)

        for (it in items) {
            val amountInput = it.toAmountInputOrNull()
            if (amountInput == null) {
                outcomes += ItemOutcome(
                    plannedItemId = it.id,
                    status = ItemOutcome.Status.SKIPPED,
                    message = "Missing quantity (grams/servings)."
                )
                continue
            }

            when (it.type) {
                PlannedItemSource.FOOD -> {
                    val res = createLogEntry.execute(
                        ref = FoodRef.Food(foodId = it.refId),
                        timestamp = timestamp,
                        amountInput = amountInput,
                        recipeBatchId = null,
                        mealSlot = mealSlot,
                        logDateIso = logDateIso
                    )

                    when (res) {
                        is CreateLogEntryUseCase.Result.Success -> {
                            logged++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.LOGGED)
                        }
                        is CreateLogEntryUseCase.Result.Blocked -> {
                            blocked++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.BLOCKED, res.message)
                        }
                        is CreateLogEntryUseCase.Result.Error -> {
                            errored++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.ERROR, res.message)
                        }
                    }
                }

                PlannedItemSource.RECIPE -> {
                    blocked++
                    outcomes += ItemOutcome(
                        plannedItemId = it.id,
                        status = ItemOutcome.Status.BLOCKED,
                        message = "Recipe requires a cooked batch to log."
                    )
                }

                PlannedItemSource.RECIPE_BATCH -> {
                    val batchId = it.refId
                    val batch = recipeBatches.getBatchById(batchId)
                    if (batch == null) {
                        errored++
                        outcomes += ItemOutcome(it.id, ItemOutcome.Status.ERROR, "Recipe batch not found.")
                        continue
                    }

                    val header = recipes.getHeaderByRecipeId(batch.recipeId)
                    if (header == null) {
                        errored++
                        outcomes += ItemOutcome(it.id, ItemOutcome.Status.ERROR, "Recipe not found for batch.")
                        continue
                    }

                    val recipeFood = foods.getById(header.foodId)
                    if (recipeFood == null) {
                        errored++
                        outcomes += ItemOutcome(it.id, ItemOutcome.Status.ERROR, "Recipe food not found.")
                        continue
                    }

                    val ref = FoodRef.Recipe(
                        recipeId = header.recipeId,
                        stableId = recipeFood.stableId,
                        displayName = recipeFood.name,
                        servingsYieldDefault = header.servingsYield
                    )

                    val res = createLogEntry.execute(
                        ref = ref,
                        timestamp = timestamp,
                        amountInput = amountInput,
                        recipeBatchId = batchId,
                        mealSlot = mealSlot,
                        logDateIso = logDateIso
                    )

                    when (res) {
                        is CreateLogEntryUseCase.Result.Success -> {
                            logged++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.LOGGED)
                        }
                        is CreateLogEntryUseCase.Result.Blocked -> {
                            blocked++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.BLOCKED, res.message)
                        }
                        is CreateLogEntryUseCase.Result.Error -> {
                            errored++
                            outcomes += ItemOutcome(it.id, ItemOutcome.Status.ERROR, res.message)
                        }
                    }
                }
            }
        }

        return Result(
            loggedCount = logged,
            blockedCount = blocked,
            errorCount = errored,
            outcomes = outcomes
        )
    }

    private fun PlannedItemEntity.toAmountInputOrNull(): AmountInput? {
        val g = grams
        if (g != null) return AmountInput.ByGrams(g)

        val s = servings
        if (s != null) return AmountInput.ByServings(s)

        return null
    }
}
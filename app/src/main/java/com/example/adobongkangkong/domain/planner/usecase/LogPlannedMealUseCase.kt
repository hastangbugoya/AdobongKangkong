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
 * Logs every item in a Planned Meal occurrence into immutable snapshot log entries.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A “planned meal” is a planner-layer occurrence container:
 *
 * - planned_meals: the meal occurrence (dateIso + slot + optional label/name)
 * - planned_items: the foods/recipes/batches planned for that meal
 *
 * This use case converts a planner occurrence into authoritative history by creating
 * immutable log entries (snapshot-at-log-time) for each planned item.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Logging a whole planned meal is not the same as logging individual items:
 *
 * - The UI action is “Log this meal”, not “log item #1”.
 * - Duplicate prevention must apply to the meal as a unit.
 * - Per-item logging can succeed/fail independently, but the meal-level action
 *   must still be recorded and guarded.
 *
 * This use case centralizes:
 *
 * - the meal-level “log once” invariant
 * - conversion of planned item quantities into AmountInput
 * - item-type routing (FOOD vs RECIPE vs RECIPE_BATCH)
 * - per-item outcome reporting for UI feedback
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * 1) Duplicate logging prevention (atomic)
 *    - Atomically marks the planned meal as logged by setting loggedAtEpochMs.
 *    - If already logged, returns immediately and does not attempt per-item logging.
 *
 * 2) Loads all planned items for the meal occurrence.
 *
 * 3) For each item:
 *    - Resolves AmountInput:
 *      - grams -> AmountInput.ByGrams
 *      - servings -> AmountInput.ByServings
 *      - neither -> SKIPPED
 *
 *    - Routes by item source:
 *      - FOOD:
 *          logs directly via CreateLogEntryUseCase with FoodRef.Food
 *
 *      - RECIPE:
 *          BLOCKED (requires cooked batch to log; phase-1 rule)
 *
 *      - RECIPE_BATCH:
 *          resolves batch -> recipe header -> recipe food
 *          then logs via CreateLogEntryUseCase with FoodRef.Recipe + recipeBatchId
 *
 * 4) Aggregates per-item outcomes and returns counts.
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param mealId Planned meal occurrence id to log.
 * @param timestamp Instant stored on each resulting log entry (ordering/posterity).
 * @param logDateIso ISO yyyy-MM-dd string stored on each log entry for day membership.
 *                   This is authoritative for “which day does this log belong to”.
 * @param mealSlot Optional slot override to stamp on resulting logs (when caller wants a specific slot).
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 *
 * @return A [Result] containing:
 * - loggedCount: number of items successfully logged
 * - blockedCount: number of items blocked by domain rules
 * - errorCount: number of items that errored unexpectedly or due to missing dependencies
 * - outcomes: per-item status + optional message (for UI messaging)
 *
 * If the meal was already logged, returns immediately with:
 * - loggedCount=0, blockedCount=0, errorCount=1
 * - one ERROR outcome indicating duplicate logging prevention
 *
 * ----------------------------------------------------------------------------
 * Edge cases handled
 * ----------------------------------------------------------------------------
 *
 * - Meal already logged -> no-op logging, returns early (prevents duplicates).
 * - Planned item missing quantity -> SKIPPED (does not crash, does not block rest).
 * - Recipe batch missing -> ERROR for that item.
 * - Recipe header missing -> ERROR for that item.
 * - Recipe food missing -> ERROR for that item.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - Meal-level duplicate guard must stay HERE.
 *   Putting a “already logged” guard inside CreateLogEntryUseCase would break meal logging
 *   because CreateLogEntryUseCase is called per-item (item #2+ would be blocked incorrectly).
 *
 * - logDateIso is authoritative for day membership.
 *   Do not derive log day from timestamp windows (Day Log bug prevention).
 *
 * - This use case intentionally blocks RECIPE (non-batch) logging.
 *   Logging recipes requires a cooked batch snapshot to preserve immutability.
 *
 * - markLoggedIfNotYet is an atomic write.
 *   If later item logging fails, the meal remains marked logged.
 *   This is intentional: “Log meal” is treated as a single user action guarded once.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Logs are immutable snapshots and must not rejoin foods later.
 * - Day membership is ISO-date based (logDateIso).
 * - This use case may read planner + recipe metadata, but the actual log write is delegated
 *   to CreateLogEntryUseCase (single canonical log writer).
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

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — LogPlannedMealUseCase invariants and notes
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - Planned meal occurrences must be loggable at most once.
 * - The meal-level guard must remain atomic:
 *   - markLoggedIfNotYet(...) must happen BEFORE iterating items.
 * - Logs are immutable snapshots and must NOT rejoin foods later.
 * - logDateIso (yyyy-MM-dd) is authoritative for day membership.
 *
 * Duplicate logging rule
 * - DO NOT move the duplicate guard into CreateLogEntryUseCase.
 *   That use case runs per-item and would incorrectly block item #2+.
 *
 * Failure semantics (intentional)
 * - Once marked logged, the meal stays logged even if one or more items error/block/skip.
 * - This encodes the action “user logged this meal” as a one-time event.
 * - If you ever want “transactional meal logging”, that requires a separate design:
 *   - transaction across markLogged + all log inserts
 *   - rollback / retry semantics
 *   - clear UX for partial failures
 *
 * Recipe vs recipe batch
 * - RECIPE (definition) is blocked intentionally.
 * - Only RECIPE_BATCH can be logged because batch context produces stable snapshots.
 *
 * Performance considerations
 * - This is IO-heavy by nature (iterates items and may resolve batch/header/food).
 * - If this becomes slow, consider batch-fetching:
 *   - recipe batches by ids
 *   - recipe headers by recipeId
 *   - foods by foodId
 * but keep logic and invariants unchanged.
 *
 * Migration / evolution notes
 * - If PlannedItemEntity adds “note” or other fields, consider whether they should be carried
 *   into log entry metadata (still snapshot-based).
 * - If you introduce “log partial meal” flows, do NOT weaken the “log once” invariant without
 *   a replacement mechanism for dedupe.
 */
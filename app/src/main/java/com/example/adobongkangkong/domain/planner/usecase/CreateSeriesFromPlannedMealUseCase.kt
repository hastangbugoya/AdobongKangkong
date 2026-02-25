package com.example.adobongkangkong.domain.planner.usecase

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.PlannedSeriesItemRepository
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Promote an existing planned meal (+ its planned items) into a recurrence series template.
 *
 * Transactional:
 * - Creates planned_series
 * - Creates one slot rule for the meal's weekday/slot/customLabel
 * - Creates planned_series_items templates from planned_items
 * - Updates the original meal to set seriesId (anchor occurrence)
 *
 * Does NOT call EnsureSeriesOccurrences; caller can do that (or create a wrapper).
 */
class CreateSeriesFromPlannedMealUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository,
    private val seriesRepo: PlannedSeriesRepository,
    private val seriesItems: PlannedSeriesItemRepository,
    private val recipeBatchLookup: RecipeBatchLookupRepository,
) {

    data class Result(
        val seriesId: Long,
        val anchorDate: LocalDate
    )

    suspend fun execute(mealId: Long): Result = db.withTransaction {
        val meal = meals.getById(mealId)
            ?: error("PlannedMeal not found: id=$mealId")

        val anchorDate = LocalDate.parse(meal.date)
        val now = System.currentTimeMillis()

        val mealItems = items.getItemsForMeal(mealId)

        // Create series
        val seriesId = seriesRepo.insertSeries(
            PlannedSeriesEntity(
                effectiveStartDate = meal.date,
                effectiveEndDate = null,
                endConditionType = PlannedSeriesEndConditionType.INDEFINITE,
                endConditionValue = null,
                sourceMealId = meal.id,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        // Slot rule for the anchor meal
        val rule = PlannedSeriesSlotRuleEntity(
            seriesId = seriesId,
            weekday = anchorDate.dayOfWeek.value,
            slot = meal.slot,
            customLabel = meal.customLabel,
            createdAtEpochMs = now
        )
        seriesRepo.replaceSlotRules(seriesId, listOf(rule))

        // Template items
        for (mi in mealItems) {
            val (foodId, recipeId) = when (mi.type) {
                PlannedItemSource.FOOD -> mi.refId to null

                PlannedItemSource.RECIPE -> null to mi.refId

                PlannedItemSource.RECIPE_BATCH -> {
                    val batch = recipeBatchLookup.getBatchById(mi.refId)
                        ?: error("Recipe batch not found: batchId=${mi.refId}")
                    null to batch.recipeId
                }
            }

            val template = PlannedSeriesItemEntity(
                seriesId = seriesId,
                foodId = foodId,
                recipeId = recipeId,
                grams = mi.grams,
                servings = mi.servings,
                note = null, // planned_items doesn't have note
                sortOrder = mi.sortOrder,
            )

            seriesItems.insert(template)
        }

        // Link anchor meal to series
        meals.update(meal.copy(seriesId = seriesId))

        Result(seriesId = seriesId, anchorDate = anchorDate)
    }
}
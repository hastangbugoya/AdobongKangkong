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
 * Promotes an existing planned meal into a reusable recurrence series template.
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

    suspend fun execute(
        mealId: Long,
        slotRulesOverride: List<CreatePlannedSeriesUseCase.SlotRuleInput>? = null,
        endConditionType: PlannedSeriesEndConditionType = PlannedSeriesEndConditionType.INDEFINITE,
        endConditionValue: String? = null,
    ): Result = db.withTransaction {
        val meal = meals.getById(mealId)
            ?: error("PlannedMeal not found: id=$mealId")

        val anchorDate = LocalDate.parse(meal.date)
        val now = System.currentTimeMillis()

        val mealItems = items.getItemsForMeal(mealId)

        val effectiveEndDate = when (endConditionType) {
            PlannedSeriesEndConditionType.UNTIL_DATE -> endConditionValue
            PlannedSeriesEndConditionType.REPEAT_COUNT,
            PlannedSeriesEndConditionType.INDEFINITE -> null
            else -> null
        }

        val normalizedEndConditionValue = when (endConditionType) {
            PlannedSeriesEndConditionType.UNTIL_DATE -> {
                require(!endConditionValue.isNullOrBlank()) {
                    "Until-date recurrence requires an end date."
                }
                endConditionValue
            }

            PlannedSeriesEndConditionType.REPEAT_COUNT -> {
                val count = endConditionValue?.toIntOrNull()
                require(count != null && count > 0) {
                    "Repeat-count recurrence requires a positive occurrence count."
                }
                count.toString()
            }

            PlannedSeriesEndConditionType.INDEFINITE -> null
            else -> null
        }

        val seriesId = seriesRepo.insertSeries(
            PlannedSeriesEntity(
                effectiveStartDate = meal.date,
                effectiveEndDate = effectiveEndDate,
                endConditionType = endConditionType,
                endConditionValue = normalizedEndConditionValue,
                sourceMealId = meal.id,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        val rules = (slotRulesOverride ?: listOf(
            CreatePlannedSeriesUseCase.SlotRuleInput(
                weekday = anchorDate.dayOfWeek.value,
                slot = meal.slot,
                customLabel = meal.customLabel,
            )
        )).map { input ->
            PlannedSeriesSlotRuleEntity(
                seriesId = seriesId,
                weekday = input.weekday,
                slot = input.slot,
                customLabel = input.customLabel,
                createdAtEpochMs = now
            )
        }
        seriesRepo.replaceSlotRules(seriesId, rules)

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
                note = null,
                sortOrder = mi.sortOrder,
            )

            seriesItems.insert(template)
        }

        meals.update(meal.copy(seriesId = seriesId))

        Result(seriesId = seriesId, anchorDate = anchorDate)
    }
}
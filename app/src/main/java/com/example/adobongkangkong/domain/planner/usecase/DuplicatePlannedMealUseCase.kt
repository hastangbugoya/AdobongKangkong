package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Duplicate a planned meal into a new independent meal instance.
 *
 * Rules:
 * - New meal row (new ID)
 * - New item rows (new IDs)
 * - Same content, no linkage
 * - No recurrence, no propagation
 * - Optional target slot override
 *
 * Important:
 * - Duplicates must preserve recipeVariantId so planned recipe variants survive copying.
 * - Duplicates must not keep recurrence/series linkage from the source meal.
 */
class DuplicatePlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {

    suspend operator fun invoke(
        sourceMealId: Long,
        targetDateIso: String,
        targetSlot: MealSlot? = null
    ): Long {
        require(sourceMealId > 0) { "sourceMealId must be > 0" }
        require(targetDateIso.isNotBlank()) { "targetDateIso must not be blank" }

        val sourceMeal: PlannedMealEntity =
            meals.getById(sourceMealId)
                ?: throw IllegalArgumentException("Source meal not found: $sourceMealId")

        val sourceItems: List<PlannedItemEntity> =
            items.getItemsForMeal(sourceMealId)

        val finalSlot = targetSlot ?: sourceMeal.slot

        val finalCustomLabel = when (finalSlot) {
            MealSlot.CUSTOM -> sourceMeal.customLabel ?: "Custom"
            else -> null
        }

        val newMealSortOrder = meals.getMaxSortOrderForDate(targetDateIso) + 1

        val newMeal = PlannedMealEntity(
            date = targetDateIso,
            slot = finalSlot,
            customLabel = finalCustomLabel,

            /*
             * Keep the user's visible meal name for now.
             *
             * If this was an auto-generated name like Lunch(Jun-30-2026), a future polish pass
             * can recompute it for the new date/slot. For now, preserve user-visible naming
             * rather than guessing whether it was custom or generated.
             */
            nameOverride = sourceMeal.nameOverride,

            sortOrder = newMealSortOrder,

            /*
             * Intentionally do not copy seriesId.
             * A duplicated meal is a standalone planned meal, not a new occurrence of the series.
             */
            seriesId = null
        )

        val newMealId = meals.insert(newMeal)

        sourceItems
            .sortedWith(compareBy<PlannedItemEntity> { it.sortOrder }.thenBy { it.id })
            .forEach { src ->
                items.insert(
                    PlannedItemEntity(
                        mealId = newMealId,
                        type = src.type,
                        refId = src.refId,
                        recipeVariantId = src.recipeVariantId,
                        grams = src.grams,
                        servings = src.servings,
                        sortOrder = src.sortOrder
                    )
                )
            }

        return newMealId
    }
}
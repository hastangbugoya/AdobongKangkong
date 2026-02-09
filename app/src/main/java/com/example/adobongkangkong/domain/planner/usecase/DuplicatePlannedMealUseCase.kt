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
            MealSlot.CUSTOM -> sourceMeal.customLabel
                ?: throw IllegalStateException("CUSTOM meal must have customLabel (mealId=$sourceMealId)")
            else -> null
        }

        val newMealSortOrder = meals.getMaxSortOrderForDate(targetDateIso) + 1

        val newMeal = PlannedMealEntity(
            date = targetDateIso,
            slot = finalSlot,
            customLabel = finalCustomLabel,
            nameOverride = sourceMeal.nameOverride,
            sortOrder = newMealSortOrder
        )

        val newMealId = meals.insert(newMeal)

        // Preserve item order exactly
        sourceItems
            .sortedWith(compareBy<PlannedItemEntity> { it.sortOrder }.thenBy { it.id })
            .forEach { src ->
                items.insert(
                    PlannedItemEntity(
                        mealId = newMealId,
                        type = src.type,
                        refId = src.refId,
                        grams = src.grams,
                        servings = src.servings,
                        sortOrder = src.sortOrder
                    )
                )
            }

        return newMealId
    }
}

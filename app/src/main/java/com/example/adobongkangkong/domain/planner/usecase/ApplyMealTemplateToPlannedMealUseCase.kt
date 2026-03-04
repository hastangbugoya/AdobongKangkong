package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Replaces the items of an existing PlannedMeal with the items from a MealTemplate.
 *
 * ## Behavior
 * - Validates planned meal exists.
 * - Validates template exists.
 * - Deletes existing planned items for the meal.
 * - Inserts planned items copied from template items, preserving order.
 *
 * ## Ordering contract
 * Template items are read in `(sortOrder, id)` order and normalized to `0..n-1`.
 *
 * ## Notes
 * - This does NOT modify PlannedMealEntity fields (slot/name/date/customLabel/etc.).
 * - This is the safest "template → planned meal" operation when the meal container already exists.
 */
class ApplyMealTemplateToPlannedMealUseCase @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository
) {
    suspend operator fun invoke(
        plannedMealId: Long,
        templateId: Long
    ) {
        require(plannedMealId > 0) { "plannedMealId must be > 0" }
        require(templateId > 0) { "templateId must be > 0" }

        val meal = plannedMeals.getById(plannedMealId)
            ?: throw IllegalArgumentException("Planned meal not found: id=$plannedMealId")

        templates.getById(templateId)
            ?: throw IllegalArgumentException("Meal template not found: id=$templateId")

        val items = templateItems.getItemsForTemplate(templateId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        plannedItems.deleteForMeal(meal.id)

        items.forEachIndexed { index, t ->
            plannedItems.insert(
                PlannedItemEntity(
                    mealId = meal.id,
                    type = t.type,
                    refId = t.refId,
                    grams = t.grams,
                    servings = t.servings,
                    sortOrder = index
                )
            )
        }
    }
}
package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Creates a new MealTemplate from an existing PlannedMeal (+ its PlannedItems).
 *
 * Notes:
 * - This is a "copy" operation. The template is independent after creation.
 * - Template item sortOrder is normalized to 0..n-1 using the planned item's current ordering.
 */
class SavePlannedMealAsTemplateUseCase @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository
) {
    suspend operator fun invoke(
        plannedMealId: Long,
        templateName: String? = null,
        defaultSlotFromMeal: Boolean = true
    ): Long {
        require(plannedMealId > 0) { "plannedMealId must be > 0" }

        val meal = plannedMeals.getById(plannedMealId)
            ?: throw IllegalArgumentException("Planned meal not found: id=$plannedMealId")

        val items = plannedItems.getItemsForMeal(plannedMealId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        val resolvedName = templateName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: meal.nameOverride
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: run {
                // Fallback: use slot token if no explicit nameOverride.
                meal.slot.name
            }

        val templateId = templates.insert(
            MealTemplateEntity(
                name = resolvedName,
                defaultSlot = if (defaultSlotFromMeal) meal.slot else null
            )
        )

        // Normalize template item ordering to 0..n-1, regardless of planned sortOrder values.
        items.forEachIndexed { index, planned ->
            templateItems.insert(
                MealTemplateItemEntity(
                    templateId = templateId,
                    type = planned.type,
                    refId = planned.refId,
                    grams = planned.grams,
                    servings = planned.servings,
                    sortOrder = index
                )
            )
        }

        return templateId
    }
}

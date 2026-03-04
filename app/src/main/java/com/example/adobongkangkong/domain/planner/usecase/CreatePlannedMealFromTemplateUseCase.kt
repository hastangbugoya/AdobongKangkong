package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Creates a new PlannedMeal occurrence from an existing MealTemplate.
 *
 * ## Behavior
 * - Validates template exists.
 * - Creates a new PlannedMealEntity for the given [dateIso] and resolved slot.
 * - Copies template items into PlannedItemEntity rows for the new meal, preserving order.
 *
 * ## Slot rules
 * Priority:
 * 1) [overrideSlot] (when non-null)
 * 2) template.defaultSlot (when non-null)
 * 3) throws if neither provides a slot
 *
 * ## Ordering contract
 * - The new planned meal is appended to the end of the day using `getMaxSortOrderForDate(dateIso) + 1`.
 * - Template items are read in `(sortOrder, id)` order and normalized to `0..n-1` for planned items.
 *
 * ## Notes
 * - This does not de-duplicate existing planned meals for the date.
 * - This does not mark the meal logged.
 * - This does not set seriesId/status/loggedAtEpochMs (defaults are used).
 */
class CreatePlannedMealFromTemplateUseCase @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository
) {
    suspend operator fun invoke(
        templateId: Long,
        dateIso: String, // yyyy-MM-dd
        overrideSlot: MealSlot? = null,
        nameOverride: String? = null
    ): Long {
        require(templateId > 0) { "templateId must be > 0" }
        require(dateIso.isNotBlank()) { "dateIso must be non-blank (yyyy-MM-dd)" }

        val template = templates.getById(templateId)
            ?: throw IllegalArgumentException("Meal template not found: id=$templateId")

        val resolvedSlot = overrideSlot
            ?: template.defaultSlot
            ?: throw IllegalArgumentException(
                "Meal slot is required (overrideSlot or template.defaultSlot). templateId=$templateId"
            )

        val resolvedNameOverride = nameOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val nextSortOrder = plannedMeals.getMaxSortOrderForDate(dateIso) + 1

        val plannedMealId = plannedMeals.insert(
            PlannedMealEntity(
                date = dateIso,
                slot = resolvedSlot,
                customLabel = null,
                nameOverride = resolvedNameOverride,
                sortOrder = nextSortOrder
            )
        )

        val items = templateItems.getItemsForTemplate(templateId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        items.forEachIndexed { index, t ->
            plannedItems.insert(
                PlannedItemEntity(
                    mealId = plannedMealId,
                    type = t.type,
                    refId = t.refId,
                    grams = t.grams,
                    servings = t.servings,
                    sortOrder = index
                )
            )
        }

        return plannedMealId
    }
}
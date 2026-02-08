package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Materializes a MealTemplate into one or more PlannedMeal instances (date + slot),
 * copying the template items into planned items.
 *
 * Semantics (minimal, predictable):
 * - Always creates a NEW planned meal per target (no attempt to reuse/merge).
 * - Planned meals are appended (sortOrder = Int.MAX_VALUE unless caller later adds reordering).
 * - Planned items are inserted in template order, with sortOrder = Int.MAX_VALUE (append strategy),
 *   but their relative insertion order remains stable via (sortOrder, id).
 *
 * Returns: list of created plannedMealIds in the same order as targets.
 */
class ApplyMealTemplateToPlanUseCase @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository,
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository
) {
    suspend operator fun invoke(
        templateId: Long,
        targets: List<ApplyMealTemplateTarget>
    ): List<Long> {
        require(templateId > 0) { "templateId must be > 0" }
        require(targets.isNotEmpty()) { "targets must not be empty" }

        val template = templates.getById(templateId)
            ?: throw IllegalArgumentException("Meal template not found: id=$templateId")

        val items = templateItems.getItemsForTemplate(templateId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        val createdMealIds = mutableListOf<Long>()

        for (target in targets) {
            require(target.dateIso.isNotBlank()) { "target.dateIso must not be blank" }

            val normalizedCustomLabel = when (target.slot) {
                MealSlot.CUSTOM -> {
                    val label = target.customLabel?.trim().orEmpty()
                    require(label.isNotBlank()) { "customLabel is required when slot == CUSTOM" }
                    label
                }
                else -> null
            }

            val mealName = target.mealNameOverride
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: template.name.trim().takeIf { it.isNotBlank() }

            val plannedMealId = plannedMeals.insert(
                PlannedMealEntity(
                    date = target.dateIso,
                    slot = target.slot,
                    customLabel = normalizedCustomLabel,
                    nameOverride = mealName,
                    sortOrder = Int.MAX_VALUE
                )
            )

            // Insert planned items in template order. We keep "append" ordering strategy.
            // Relative order remains stable via ORDER BY sortOrder, id.
            items.forEach { tItem ->
                plannedItems.insert(
                    PlannedItemEntity(
                        mealId = plannedMealId,
                        type = tItem.type,
                        refId = tItem.refId,
                        grams = tItem.grams,
                        servings = tItem.servings,
                        sortOrder = Int.MAX_VALUE
                    )
                )
            }

            createdMealIds += plannedMealId
        }

        return createdMealIds
    }
}

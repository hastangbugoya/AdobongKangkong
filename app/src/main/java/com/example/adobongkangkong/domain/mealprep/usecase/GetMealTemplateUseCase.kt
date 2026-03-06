package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.mealprep.model.FoodMealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.MealTemplate
import com.example.adobongkangkong.domain.mealprep.model.MealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.PlannedQuantity
import com.example.adobongkangkong.domain.mealprep.model.RecipeBatchMealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.RecipeMealTemplateItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import javax.inject.Inject

class GetMealTemplateUseCase @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository
) {
    suspend operator fun invoke(templateId: Long): MealTemplate {
        require(templateId > 0L) { "templateId must be > 0" }

        val template = templates.getById(templateId)
            ?: throw IllegalArgumentException("Meal template not found: id=$templateId")

        val items = templateItems.getItemsForTemplate(templateId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { entity ->
                entity.type.toDomainItem(
                    refId = entity.refId,
                    quantity = PlannedQuantity(
                        grams = entity.grams,
                        servings = entity.servings
                    )
                )
            }

        return MealTemplate(
            id = template.id,
            name = template.name,
            defaultSlot = template.defaultSlot,
            items = items
        )
    }

    private fun PlannedItemSource.toDomainItem(
        refId: Long,
        quantity: PlannedQuantity
    ): MealTemplateItem = when (this) {
        PlannedItemSource.FOOD -> FoodMealTemplateItem(foodId = refId, quantity = quantity)
        PlannedItemSource.RECIPE -> RecipeMealTemplateItem(recipeId = refId, quantity = quantity)
        PlannedItemSource.RECIPE_BATCH -> RecipeBatchMealTemplateItem(recipeBatchId = refId, quantity = quantity)
    }
}

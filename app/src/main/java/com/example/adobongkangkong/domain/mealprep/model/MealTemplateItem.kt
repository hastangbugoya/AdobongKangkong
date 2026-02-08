package com.example.adobongkangkong.domain.mealprep.model

sealed interface MealTemplateItem {
    val quantity: PlannedQuantity
}

data class FoodMealTemplateItem(
    val foodId: Long,
    override val quantity: PlannedQuantity
) : MealTemplateItem

data class RecipeBatchMealTemplateItem(
    val recipeBatchId: Long,
    override val quantity: PlannedQuantity
) : MealTemplateItem

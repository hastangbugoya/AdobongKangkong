package com.example.adobongkangkong.domain.model

data class FoodHardDeleteBlockers(
    val isRecipeFood: Boolean,
    val logsUsingStableId: Int,
    val plannedItemsUsingFoodId: Int,
    val recipeIngredientsUsingFoodId: Int,
    val recipeBatchesUsingBatchFoodId: Int
) {
    val isBlocked: Boolean =
        isRecipeFood ||
                logsUsingStableId > 0 ||
                plannedItemsUsingFoodId > 0 ||
                recipeIngredientsUsingFoodId > 0 ||
                recipeBatchesUsingBatchFoodId > 0
}

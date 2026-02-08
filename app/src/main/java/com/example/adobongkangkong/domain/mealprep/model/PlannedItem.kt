package com.example.adobongkangkong.domain.mealprep.model

sealed interface PlannedItem {
    val quantity: PlannedQuantity
}

data class FoodPlannedItem(
    val foodId: Long,
    override val quantity: PlannedQuantity
) : PlannedItem

data class RecipeBatchPlannedItem(
    val recipeBatchId: Long,
    override val quantity: PlannedQuantity
) : PlannedItem
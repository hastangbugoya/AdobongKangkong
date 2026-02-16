package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import kotlinx.coroutines.flow.Flow

data class PlannedItemWithDateRow(
    val dateIso: String,              // yyyy-MM-dd
    val type: PlannedItemSource,       // FOOD | RECIPE | RECIPE_BATCH
    val refId: Long,                   // foodId or recipeFoodId etc.
    val grams: Double?,
    val servings: Double?
)

interface PlannedItemsRangeRepository {
    fun observePlannedItemsInRange(startIso: String, endIso: String): Flow<List<PlannedItemWithDateRow>>
}
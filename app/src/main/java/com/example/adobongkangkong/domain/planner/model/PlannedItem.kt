package com.example.adobongkangkong.domain.planner.model

// domain/planner/model/PlannedItem.kt
data class PlannedItem(
    val id: Long,
    val sourceType: PlannedItemSource,
    val sourceId: Long,
    val qtyGrams: Double?,
    val qtyServings: Double?
)

package com.example.adobongkangkong.domain.planner.model

data class PlannedItem(
    val id: Long,
    val sourceType: PlannedItemSource,
    val sourceId: Long,
    val qtyGrams: Double?,
    val qtyServings: Double?,
    /**
     * Optional resolved display title for UI (e.g., food name or recipe name).
     * This is derived (not stored in planner DB) and may be null if lookup fails.
     */
    val title: String?
)

package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import java.time.Instant

data class LogEntry(
    val id: Long = 0,
    val stableId: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val timestamp: Instant,
    val logDateIso: String,
    val itemName: String,
    val foodStableId: String?,
    val nutrients: NutrientMap,

    /**
     * Preserved user-entered amount intent for this log row.
     *
     * These fields are stored alongside the immutable nutrient snapshot so log rows can later be
     * reopened in Quick Add edit mode without reverse-deriving the user's original quantity basis.
     */
    val amount: Double = 1.0,
    val unit: LogUnit = LogUnit.ITEM,

    // ✅ NEW: provenance + UI grouping
    val recipeBatchId: Long? = null,
    val gramsPerServingCooked: Double? = null,
    val mealSlot: MealSlot? = null
)
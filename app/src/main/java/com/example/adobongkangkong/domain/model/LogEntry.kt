package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import java.time.Instant

data class LogEntry(
    val id: Long = 0,
    val timestamp: Instant,
    val logDateIso: String,
    val itemName: String,
    val foodStableId: String?,
    val nutrients: NutrientMap,

    // ✅ NEW: provenance + UI grouping
    val recipeBatchId: Long? = null,
    val gramsPerServingCooked: Double? = null,
    val mealSlot: MealSlot? = null
)

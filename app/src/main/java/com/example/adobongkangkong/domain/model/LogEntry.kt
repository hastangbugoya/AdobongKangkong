package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import java.time.Instant

data class LogEntry(
    val id: Long = 0,
    val timestamp: Instant,
    val itemName: String,
    val foodStableId: String?,
    val nutrients: NutrientMap
)

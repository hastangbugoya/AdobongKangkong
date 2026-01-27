package com.example.adobongkangkong.domain.model

import java.time.Instant

data class TodayLogItem(
    val logId: Long,
    val itemName: String,
    val timestamp: Instant,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
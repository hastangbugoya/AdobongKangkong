package com.example.adobongkangkong.domain.model

import java.time.Instant

data class TodayLogItem(
    val logId: Long,
    val timestamp: Instant,
    val foodName: String,
    val servings: Double,
    val caloriesKcal: Double
)
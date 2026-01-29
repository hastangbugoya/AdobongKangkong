package com.example.adobongkangkong.ui.daylog.model

import java.time.Instant

data class DayLogRow(
    val logId: Long,
    val itemName: String,
    val timestamp: Instant,
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?
)

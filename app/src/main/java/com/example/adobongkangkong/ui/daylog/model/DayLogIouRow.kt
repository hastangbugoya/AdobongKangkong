package com.example.adobongkangkong.ui.daylog.model

/**
 * UI model for an IOU row in the Day Log screen.
 *
 * IOUs do not affect daily totals, but they may optionally carry rough macro
 * estimates for reminder/display purposes.
 */
data class DayLogIouRow(
    val iouId: Long,
    val description: String,
    val estimatedCaloriesKcal: Double?,
    val estimatedProteinG: Double?,
    val estimatedCarbsG: Double?,
    val estimatedFatG: Double?
)
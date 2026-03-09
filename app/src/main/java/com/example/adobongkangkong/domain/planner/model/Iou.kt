package com.example.adobongkangkong.domain.planner.model

import java.time.Instant

/**
 * Domain model for a planner IOU.
 *
 * IOUs are narrative placeholders that may carry optional macro-only estimates.
 */
data class Iou(
    val id: Long,
    val description: String,
    val estimatedCaloriesKcal: Double?,
    val estimatedProteinG: Double?,
    val estimatedCarbsG: Double?,
    val estimatedFatG: Double?,
    val createdAt: Instant,
    val updatedAt: Instant
)

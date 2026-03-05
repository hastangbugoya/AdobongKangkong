package com.example.adobongkangkong.domain.planner.model

import java.time.Instant

/**
 * Domain model for a planner IOU.
 *
 * IOUs are narrative-only placeholders and carry no nutrition.
 */
data class Iou(
    val id: Long,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

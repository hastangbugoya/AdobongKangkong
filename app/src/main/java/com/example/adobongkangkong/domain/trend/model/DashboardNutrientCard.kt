package com.example.adobongkangkong.domain.trend.model

/**
 * Dashboard-ready nutrient card, combining:
 * - nutrient identity + metadata (displayName/unit)
 * - today's consumed amount
 * - user target (min/target/max)
 * - computed status (LOW/OK/HIGH/NO_TARGET)
 * - rolling decorations (average + ok streak)
 *
 * This is intentionally UI-friendly but still a domain model:
 * it contains no Compose/UI types, only primitive values + enums.
 */
data class DashboardNutrientCard(
    val code: String,
    val displayName: String,
    val unit: String?,

    val consumedToday: Double,

    val minPerDay: Double?,
    val targetPerDay: Double?,
    val maxPerDay: Double?,

    val status: TargetStatus,

    /** Rolling average for this nutrient across the selected window (nullable if not available). */
    val rollingAverage: Double?,

    /** OK streak length (0 if none). */
    val okStreakDays: Int,

    /** Optional IOU estimate reminder for this nutrient (not included in consumed/status). */
    val iouEstimate: Double? = null
)

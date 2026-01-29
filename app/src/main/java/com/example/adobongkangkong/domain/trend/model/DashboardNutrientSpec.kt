package com.example.adobongkangkong.domain.trend.model

/**
 * Dashboard-ready nutrient definition:
 * - `code` is the canonical nutrient code used everywhere in analytics (e.g. "protein_g").
 * - `displayName` + `unit` come from nutrient metadata (DB seed/import).
 * - `targetPerDay` comes from user targets (nullable means "no target configured").
 *
 * This model is intentionally "config-only" (no consumed amounts).
 * Amounts + status (LOW/OK/HIGH) are computed downstream by the daily/rolling aggregation pipeline.
 */
data class DashboardNutrientSpec(
    val code: String,
    val displayName: String,
    val unit: String?,
    val targetPerDay: Double?,
    val minPerDay: Double?,
    val maxPerDay: Double?
)
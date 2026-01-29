package com.example.adobongkangkong.domain.trend.model

data class NutrientStreak(
    val nutrientCode: String,
    /** consecutive days ending at endDate satisfying predicate (default: OK) */
    val days: Int,
    val status: TargetStatus
)
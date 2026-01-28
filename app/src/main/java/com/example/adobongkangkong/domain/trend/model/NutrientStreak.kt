package com.example.adobongkangkong.domain.trend.model

import com.example.adobongkangkong.domain.model.TargetStatus

data class NutrientStreak(
    val nutrientCode: String,
    /** consecutive days ending at endDate satisfying predicate (default: OK) */
    val days: Int,
    val status: TargetStatus
)
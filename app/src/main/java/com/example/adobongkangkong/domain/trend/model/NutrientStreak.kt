package com.example.adobongkangkong.domain.trend.model

import com.example.adobongkangkong.domain.model.TargetStatus

data class NutrientStreak(
    val nutrientCode: String,
    val days: Int,
    val status: TargetStatus // usually OK or LOW/HIGH depending on what you track
)
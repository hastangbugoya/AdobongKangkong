package com.example.adobongkangkong.domain.model

data class DailyNutrientStatus(
    val nutrientCode: String,
    val consumed: Double,
    val min: Double?,
    val target: Double?,
    val max: Double?,
    val status: TargetStatus
)
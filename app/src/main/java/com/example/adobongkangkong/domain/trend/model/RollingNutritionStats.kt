package com.example.adobongkangkong.domain.trend.model

data class RollingNutritionStats(
    val averages: RollingNutritionAverages,
    val okStreaks: List<NutrientStreak>
)
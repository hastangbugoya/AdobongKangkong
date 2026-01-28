package com.example.adobongkangkong.domain.trend.model

import com.example.adobongkangkong.domain.trend.model.NutrientStreak
import com.example.adobongkangkong.domain.trend.model.RollingNutritionAverages

data class RollingNutritionStats(
    val averages: RollingNutritionAverages,
    val okStreaks: List<NutrientStreak>
)
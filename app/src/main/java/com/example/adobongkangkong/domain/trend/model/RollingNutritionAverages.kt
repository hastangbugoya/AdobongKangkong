package com.example.adobongkangkong.domain.trend.model

import java.time.LocalDate

data class RollingNutritionAverages(
    val endDate: LocalDate,
    val days: Int,
    val averageByCode: Map<String, Double>
)
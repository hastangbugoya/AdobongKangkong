package com.example.adobongkangkong.domain.model

import java.time.LocalDate

data class DailyNutritionTotals(
    val date: LocalDate,
    val totalsByCode: Map<String, Double>
)

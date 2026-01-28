package com.example.adobongkangkong.domain.model

data class DailyNutritionSummary(
    val totals: DailyNutritionTotals,
    val statuses: List<DailyNutrientStatus>
)
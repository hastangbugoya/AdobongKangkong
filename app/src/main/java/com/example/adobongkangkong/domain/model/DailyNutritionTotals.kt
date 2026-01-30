package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import java.time.LocalDate

data class DailyNutritionTotals(
    val date: LocalDate,
    val totalsByCode: NutrientMap
)

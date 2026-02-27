package com.example.adobongkangkong.domain.compliance.model

import java.time.LocalDate

data class WeeklyCompliance(
    val weekStart: LocalDate,
    val weekDates: List<LocalDate>,                 // still useful for UI ordering
    val rows: List<WeeklyNutrientRow>
)
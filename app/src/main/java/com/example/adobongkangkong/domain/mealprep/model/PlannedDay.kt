package com.example.adobongkangkong.domain.mealprep.model

import java.time.LocalDate

data class PlannedDay(
    val date: LocalDate,
    val meals: List<PlannedMeal>,
    val notes: String? = null
)

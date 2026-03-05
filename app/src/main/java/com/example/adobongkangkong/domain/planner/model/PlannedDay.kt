package com.example.adobongkangkong.domain.planner.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import java.time.LocalDate

// domain/planner/model/PlannedDay.kt
data class PlannedDay(
    val date: LocalDate,
    val mealsBySlot: Map<MealSlot, List<PlannedMeal>>,
    val ious: List<PlannerIou> = emptyList()
)

package com.example.adobongkangkong.domain.planner.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import java.time.LocalDate

data class PlannedMeal(
    val id: Long,
    val date: LocalDate,
    val slot: MealSlot,
    val title: String?,
    val items: List<PlannedItem>
)
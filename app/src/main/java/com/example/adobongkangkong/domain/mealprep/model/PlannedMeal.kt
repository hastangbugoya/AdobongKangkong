package com.example.adobongkangkong.domain.mealprep.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import java.time.LocalDate

data class PlannedMeal(
    val id: Long,
    val date: LocalDate,
    val slot: MealSlot,
    val customLabel: String? = null,
    val nameOverride: String? = null,
    val items: List<PlannedItem>,
    val sortOrder: Int
)

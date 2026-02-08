package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import java.time.LocalDate

data class PlannerDayUiState(
    val date: LocalDate,
    val isLoading: Boolean = false,
    val day: PlannedDay? = null,
    val errorMessage: String? = null,
    val addSheetSlot: MealSlot? = null
){
    /**
     * UI convenience: always provide all slots, even if day is null/empty.
     */
    val mealsBySlot: Map<MealSlot, List<com.example.adobongkangkong.domain.planner.model.PlannedMeal>>
        get() {
            val base = MealSlot.entries.associateWith { emptyList<com.example.adobongkangkong.domain.planner.model.PlannedMeal>() }
            val actual = day?.mealsBySlot ?: emptyMap()
            return base.toMutableMap().apply { putAll(actual) }
        }
}


package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import java.time.LocalDate

data class PlannerDayUiState(
    val date: LocalDate,
    val isLoading: Boolean = false,
    val day: PlannedDay? = null,
    val errorMessage: String? = null,
    val addSheet: AddSheetState? = null,
    val duplicateSheet: DuplicateSheetState? = null,

    /**
     * One-shot snackbar request for "Removed • Undo".
     * Screen should show it and then notify VM via UndoSnackbarConsumed.
     */
    val undo: UndoUiState? = null
) {
    val mealsBySlot: Map<MealSlot, List<PlannedMeal>>
        get() {
            val base = MealSlot.entries.associateWith { emptyList<PlannedMeal>() }
            val actual = day?.mealsBySlot ?: emptyMap()
            return base.toMutableMap().apply { putAll(actual) }
        }
}

data class DuplicateSheetState(
    val sourceMealId: Long,
    val selectedDates: List<LocalDate>,
    val isDuplicating: Boolean = false,
    val errorMessage: String? = null
)

data class UndoUiState(
    val id: Long,
    val message: String
)

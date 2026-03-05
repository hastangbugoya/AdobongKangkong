package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.model.MacroTotals
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
    val undo: UndoUiState? = null,

    /** Macro totals per planned meal (mealId -> totals) for the currently loaded day. */
    val mealMacroTotals: Map<Long, MacroTotals> = emptyMap(),

    /** Macro totals for the entire day (sum of all planned meals/items). */
    val dayMacroTotals: MacroTotals = MacroTotals()
) {
    val mealsBySlot: Map<MealSlot, List<PlannedMeal>>
        get() {
            val actual = day?.mealsBySlot.orEmpty()
            if (actual.size == MealSlot.entries.size) return actual
            return MealSlot.entries.associateWith { slot -> actual[slot].orEmpty() }
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

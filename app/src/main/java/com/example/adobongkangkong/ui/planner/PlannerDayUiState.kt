package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.model.MacroTotals
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
    val undo: UndoUiState? = null,

    /** Macro totals per planned meal (mealId -> totals) for the currently loaded day. */
    val mealMacroTotals: Map<Long, MacroTotals> = emptyMap(),

    /** Macro totals for the entire day (sum of all planned meals/items). */
    val dayMacroTotals: MacroTotals = MacroTotals(),

    /**
     * Day-log item names grouped by stamped meal slot for the currently selected date.
     *
     * Phase 1 banner use:
     * - awareness only
     * - no planner-item reconciliation yet
     * - names only, no quantities
     */
    val loggedNamesBySlot: Map<MealSlot, List<String>> = emptyMap(),

    /** IOU editor dialog state (null when closed). */
    val iouEditor: IouEditorState? = null,

    /** Recurring series editor state (null when closed). */
    val recurringEditor: RecurringEditorState? = null
) {
    val mealsBySlot: Map<MealSlot, List<PlannedMeal>>
        get() {
            val actual = day?.mealsBySlot.orEmpty()
            if (actual.size == MealSlot.entries.size) return actual
            return MealSlot.entries.associateWith { slot -> actual[slot].orEmpty() }
        }
}

data class IouEditorState(
    val iouId: Long? = null,
    val description: String = "",
    val caloriesText: String = "",
    val proteinText: String = "",
    val carbsText: String = "",
    val fatText: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

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

enum class RecurrenceFrequencyUi {
    DAILY,
    WEEKLY,
}

enum class RecurrenceEndConditionUi {
    INDEFINITE,
    UNTIL_DATE,
    REPEAT_COUNT,
}

data class RecurringDayRuleUiState(
    val weekday: Int,
    val isEnabled: Boolean,
    val slot: MealSlot,
    val customLabel: String? = null,
)

data class RecurringEditorState(
    val mealId: Long,
    val anchorWeekday: Int,
    val frequency: RecurrenceFrequencyUi,
    val rules: List<RecurringDayRuleUiState>,

    /**
     * PlannedSeries end condition editor state.
     *
     * INDEFINITE
     * - no explicit end
     *
     * UNTIL_DATE
     * - inclusive end date
     *
     * REPEAT_COUNT
     * - total intended occurrences for this series
     */
    val endConditionType: RecurrenceEndConditionUi = RecurrenceEndConditionUi.INDEFINITE,

    /**
     * Inclusive end date used when endConditionType == UNTIL_DATE.
     */
    val endDate: LocalDate? = null,

    /**
     * Raw text field for repeat-count entry so the UI can use a stable integer input
     * without cursor jumping/reformat churn.
     */
    val repeatCountText: String = "",

    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
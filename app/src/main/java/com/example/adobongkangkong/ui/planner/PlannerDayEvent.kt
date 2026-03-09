package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot

sealed interface PlannerDayEvent {
    data object Back : PlannerDayEvent
    data object PickDate : PlannerDayEvent
    data object PrevDay : PlannerDayEvent
    data object NextDay : PlannerDayEvent

    data class AddMeal(val slot: MealSlot) : PlannerDayEvent
    data object DismissAddSheet : PlannerDayEvent

    /**
     * Open the full Planned Meal editor screen for this slot.
     *
     * NOTE: This is used by the "+ Add" button in the slot header.
     */
    data class OpenMealPlanner(val slot: MealSlot) : PlannerDayEvent

    /**
     * Open the template picker (navigation-only).
     *
     * The ViewModel does not navigate; PlannerDayRoute/NavHost handles navigation.
     */
    data class OpenTemplatePicker(val slot: MealSlot?) : PlannerDayEvent

    data class OpenMeal(val mealId: Long) : PlannerDayEvent

    // Bottom sheet field edits
    data class UpdateAddSheetCustomLabel(val value: String) : PlannerDayEvent
    data class UpdateAddSheetName(val value: String) : PlannerDayEvent

    // Sheet action
    data object CreateMealIfNeeded : PlannerDayEvent
    data object CreateAnotherMeal : PlannerDayEvent

    // Add item to planned meal
    data class StartAddItem(val mode: AddItemMode) : PlannerDayEvent
    data object CancelAddItem : PlannerDayEvent
    data object ConfirmAddItem : PlannerDayEvent

    data class UpdateAddQuery(val value: String) : PlannerDayEvent
    data class SelectSearchResult(val id: Long, val title: String) : PlannerDayEvent

    data class UpdateAddGrams(val value: String) : PlannerDayEvent
    data class UpdateAddServings(val value: String) : PlannerDayEvent

    // Remove an EMPTY planned meal container
    data class RemoveEmptyPlannedMeal(val mealId: Long) : PlannerDayEvent

    // Remove an item from a planned meal
    data class RemovePlannedItem(val itemId: Long) : PlannerDayEvent

    data class UndoRemovePlannedItem(val undoId: Long) : PlannerDayEvent
    data class UndoSnackbarConsumed(val undoId: Long) : PlannerDayEvent

    // Existing entry point from the meal card ("Duplicate" button)
    data class DuplicateMeal(val mealId: Long) : PlannerDayEvent

    // Duplicate -> choose date(s)
    data class OpenDuplicateSheet(val mealId: Long) : PlannerDayEvent
    data object DismissDuplicateSheet : PlannerDayEvent
    data object DuplicateAddToday : PlannerDayEvent
    data object DuplicateAddTomorrow : PlannerDayEvent
    data class DuplicateAddDate(val dateIso: String) : PlannerDayEvent
    data class DuplicateRemoveDate(val dateIso: String) : PlannerDayEvent
    data object ConfirmDuplicateDates : PlannerDayEvent

    // Debug: create a sample recurring series
    data object DebugCreateSampleSeries : PlannerDayEvent

    // Promote an existing meal into a recurring series
    data class MakeMealRecurring(val mealId: Long) : PlannerDayEvent

    data class LogMeal(val mealId: Long) : PlannerDayEvent

    // Save planned meal as template
    data class SaveMealAsTemplate(val mealId: Long) : PlannerDayEvent

    // Create a new planned meal from an existing template.
    data class CreateMealFromTemplate(
        val templateId: Long,
        val overrideSlot: MealSlot? = null
    ) : PlannerDayEvent

    // ------------------------------------------------------------
    // IOUs
    // ------------------------------------------------------------

    /** Open the IOU editor in "create" mode. */
    data object OpenCreateIou : PlannerDayEvent

    /** Open the IOU editor for an existing IOU. */
    data class OpenEditIou(val iouId: Long) : PlannerDayEvent

    /** Close the IOU editor dialog. */
    data object DismissIouEditor : PlannerDayEvent

    /** Update the IOU description field while editing. */
    data class UpdateIouDescription(val value: String) : PlannerDayEvent

    /** Update optional macro estimate fields while editing. */
    data class UpdateIouCaloriesText(val value: String) : PlannerDayEvent
    data class UpdateIouProteinText(val value: String) : PlannerDayEvent
    data class UpdateIouCarbsText(val value: String) : PlannerDayEvent
    data class UpdateIouFatText(val value: String) : PlannerDayEvent

    /** Persist the IOU currently being edited (create or update). */
    data object SaveIou : PlannerDayEvent

    /** Delete an IOU. */
    data class DeleteIou(val iouId: Long) : PlannerDayEvent
}

package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot

sealed interface PlannerDayEvent {
    data object Back : PlannerDayEvent
    data object PickDate : PlannerDayEvent
    data object PrevDay : PlannerDayEvent
    data object NextDay : PlannerDayEvent

    data class AddMeal(val slot: MealSlot) : PlannerDayEvent
    data object DismissAddSheet : PlannerDayEvent

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

    data class DuplicateMeal(val mealId: Long) : PlannerDayEvent
}

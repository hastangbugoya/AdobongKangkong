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
}


package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot

sealed interface PlannerDayEvent {
    data object Back : PlannerDayEvent
    data object PickDate : PlannerDayEvent
    data object PrevDay : PlannerDayEvent
    data object NextDay : PlannerDayEvent
    data class AddMeal(val slot: MealSlot) : PlannerDayEvent
    data class OpenMeal(val mealId: Long) : PlannerDayEvent
    object DismissAddSheet : PlannerDayEvent
}

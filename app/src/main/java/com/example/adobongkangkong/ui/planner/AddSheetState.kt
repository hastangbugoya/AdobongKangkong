package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
data class AddSheetState(
    val slot: MealSlot,
    val isCreating: Boolean,
    val createdMealId: Long?,
    val customLabel: String?,
    val nameOverride: String?,
    val errorMessage: String? = null
)
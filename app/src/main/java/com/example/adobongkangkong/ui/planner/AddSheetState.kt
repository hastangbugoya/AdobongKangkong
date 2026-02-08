package com.example.adobongkangkong.ui.planner

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.ui.planner.model.FoodSearchRow

data class AddSheetState(
    val slot: MealSlot,
    val isCreating: Boolean,
    val createdMealId: Long?,
    val customLabel: String?,
    val nameOverride: String?,
    val errorMessage: String? = null,

    // Add items to planned meal
    val addItemMode: AddItemMode = AddItemMode.NONE,

        // Qty entry
    val gramsText: String = "",
    val servingsText: String = "",

    // Search + selection
    val query: String = "",
    val results: List<FoodSearchRow> = emptyList(),
    val selectedRefId: Long? = null,
    val selectedTitle: String? = null,
    val isSearching: Boolean = false,

    val isAddingItem: Boolean = false,
    val addItemError: String? = null
)

enum class AddItemMode { NONE, FOOD, RECIPE }
package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlannedMealEditorViewModel : ViewModel(), MealEditorContract {

    private val _state = MutableStateFlow(MealEditorUiState(mealId = null))

    override val state: StateFlow<MealEditorUiState>
        get() = _state

    override fun setName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    override fun addFood(foodId: Long) {
        // implement using PlannedItemRepository
    }

    override fun updateServings(itemId: Long, servings: Double) {
    }

    override fun removeItem(itemId: Long) {
    }

    override suspend fun save() {
    }
}
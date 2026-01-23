package com.example.adobongkangkong.ui.log

import com.example.adobongkangkong.domain.model.Food

enum class InputMode {
    SERVINGS,
    SERVING_UNIT,   // tbsp / cup / ml etc
    GRAMS
}

data class QuickAddState(
    val query: String = "",
    val results: List<Food> = emptyList(),

    val selectedFood: Food? = null,

    // canonical
    val servings: Double = 1.0,

    // derived UI values
    val servingUnitAmount: Double? = null,
    val gramsAmount: Double? = null,

    val inputMode: InputMode = InputMode.SERVINGS,
    val isSaving: Boolean = false
)


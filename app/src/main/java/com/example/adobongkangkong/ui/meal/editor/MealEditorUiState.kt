package com.example.adobongkangkong.ui.meal.editor

enum class MealEditorMode { PLANNED, TEMPLATE }
data class MealEditorUiState(

    val mealId: Long?,

    val name: String = "",
    val mode: MealEditorMode,
    val subtitle: String? = null, // e.g. "Mon • Lunch" or null for templates
    val items: List<Item> = emptyList(),

    val isSaving: Boolean = false,
    val canSave: Boolean = true,
    val errorMessage: String? = null,
    val isDirty: Boolean = false,
    val warnings: List<String> = emptyList()
) {

    data class Item(
        val lineId: String,
        val id: Long?,
        val foodId: Long,
        val foodName: String,
        val servings: String,
        val grams: Double? = null,
        val milliliters: Double? = null
    )
}
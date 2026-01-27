package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit

data class NutrientRowUi(
    val nutrientId: Long,
    val name: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val amount: String // keep as String for text field editing
)

data class FoodEditorState(
    val foodId: Long? = null,
    val stableId: String? = null,
    val name: String = "",
    val brand: String = "",
    val servingSize: String = "1.0",
    val servingUnit: ServingUnit = ServingUnit.G,
    val gramsPerServing: String = "",
    val servingsPerPackage: String = "",

    val nutrientRows: List<NutrientRowUi> = emptyList(),

    val nutrientSearchQuery: String = "",
    val nutrientSearchResults: List<NutrientSearchResultUi> = emptyList(),

    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

data class NutrientSearchResultUi(
    val id: Long,
    val name: String,
    val unit: NutrientUnit,
    val category: NutrientCategory
)


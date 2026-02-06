package com.example.adobongkangkong.ui.food

import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food

enum class FoodsFilter { ALL, FOODS_ONLY, RECIPES_ONLY }

data class FoodsListState(
    val query: String = "",
    val filter: FoodsFilter = FoodsFilter.ALL,
    val items: List<FoodListItemUiModel> = emptyList()
)

data class FoodListItemUiModel(
    val food: Food,
    val goalFlags: FoodGoalFlagsEntity?
    // DO NOT FILL YET (future-you note):
    // This is intentionally omitted for now.
    // Nutrient / macro previews will likely be added here later once we decide:
    //  - batch vs per-item loading
    //  - macro-only vs full nutrient preview
    //  - serving vs per-100g display rules
    //
    // See NutrientBasisScaler + its tests before adding anything here.
    // val macroPreview: FoodMacroPreviewUi?
)
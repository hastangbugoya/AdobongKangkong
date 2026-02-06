package com.example.adobongkangkong.ui.food

import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food

enum class FoodsFilter { ALL, FOODS_ONLY, RECIPES_ONLY }

data class FoodsListState(
    val query: String = "",
    val filter: FoodsFilter = FoodsFilter.ALL,
    val items: List<FoodListItemUiModel> = emptyList(),
    // DO NOT TOUCH THIS (future-you note):
    // Macro previews are computed in the VM as a separate map keyed by foodId.
    // We intentionally keep this out of FoodListItemUiModel for now to avoid
    // forcing a specific loading strategy into UI code.
    val macroPreviewByFoodId: Map<Long, FoodMacroPreviewUi> = emptyMap(),
)

/** Lightweight macro preview for list rows (per current serving). */
data class FoodMacroPreviewUi(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val sugarsG: Double? = null,
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
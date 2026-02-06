package com.example.adobongkangkong.ui.food

import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food

enum class FoodsFilter { ALL, FOODS_ONLY, RECIPES_ONLY }

enum class FoodSortKey(val label: String) {
    NAME("Name"),
    CALORIES("Calories"),
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FAT("Fat"),
    SUGAR("Sugar"), // optional / nice-to-have
}

enum class SortDirection(val label: String) { ASC("Asc"), DESC("Desc") }

data class FoodSortState(
    val key: FoodSortKey = FoodSortKey.NAME,
    val direction: SortDirection = SortDirection.ASC,
) {
    val showExtraMetricOnRow: Boolean
        get() = key in setOf(FoodSortKey.PROTEIN, FoodSortKey.CARBS, FoodSortKey.FAT, FoodSortKey.SUGAR)
}

data class FoodsListState(
    val query: String = "",
    val filter: FoodsFilter = FoodsFilter.ALL,
    val sort: FoodSortState = FoodSortState(),
    val rows: List<FoodsListRowUiModel> = emptyList(),
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


data class FoodsListRowUiModel(
    val foodId: Long,
    val name: String,
    val brandText: String, // must always be visible
    val caloriesPerServingText: String, // must always be visible
    val extraMetricText: String?, // shown only when sorting by macro (not calories)
    val isRecipe: Boolean,
    val goalFlags: FoodGoalFlagsEntity?,
)
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
)
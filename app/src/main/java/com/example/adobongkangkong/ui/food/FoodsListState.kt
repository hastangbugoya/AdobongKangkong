package com.example.adobongkangkong.ui.food

import com.example.adobongkangkong.domain.model.Food

enum class FoodsFilter { ALL, FOODS_ONLY, RECIPES_ONLY }

data class FoodsListState(
    val query: String = "",
    val filter: FoodsFilter = FoodsFilter.ALL,
    val items: List<Food> = emptyList()
)

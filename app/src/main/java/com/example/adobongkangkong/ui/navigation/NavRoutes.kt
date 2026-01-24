package com.example.adobongkangkong.ui.navigation

object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val RECIPE_NEW = "recipe/new"
    const val FOOD_NEW = "food/new"

    const val FOOD_DETAILS = "food/{foodId}"
    const val FOOD_EDIT = "food/edit/{foodId}"

    const val RECIPE_EDIT = "recipe/edit/{foodId}"

    fun recipeEdit(foodId: Long) = "recipe/edit/$foodId"

    fun foodDetails(foodId: Long) = "food/$foodId"

    fun foodEdit(foodId: Long) = "food/edit/$foodId"
}

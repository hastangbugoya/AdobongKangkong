package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft

internal fun RecipeDraft.toRecipe(): Recipe =
    Recipe(
        name = name,
        servingsYield = servingsYield,          // if Recipe expects Double?, Kotlin will auto-box
        totalYieldGrams = totalYieldGrams,
        ingredients = ingredients.map { it.toRecipeIngredient() },
        id = TODO()
    )

internal fun RecipeIngredientDraft.toRecipeIngredient(): RecipeIngredient =
    RecipeIngredient(
        foodId = foodId,
        servings = ingredientServings
    )
package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.recipes.RecipeLogInput

internal fun AmountInput.toRecipeLogInput(): RecipeLogInput =
    when (this) {
        is AmountInput.ByGrams -> RecipeLogInput.ByCookedGrams(grams = grams)
        is AmountInput.ByServings -> RecipeLogInput.ByServings(servings = servings)
    }

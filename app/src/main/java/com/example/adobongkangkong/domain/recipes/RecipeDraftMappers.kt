package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
/**
 * ⚠️ IMPORTANT ARCHITECTURAL NOTE
 *
 * RecipeDraft and its mappers exist ONLY for:
 *  - UI editing
 *  - macro / nutrition preview
 *
 * They MUST NOT be used for:
 *  - logging recipes
 *  - creating log entries
 *  - persistence
 *
 * Logging MUST load persisted RecipeEntity + RecipeIngredientEntity
 * from the database to ensure stable identity and deterministic snapshots.
 *
 * If you are calling this mapper from a logging path,
 * you are almost certainly doing the wrong thing.
 */



/**
 * Converts a RecipeDraft into a domain Recipe for PREVIEW ONLY.
 *
 * This mapping intentionally ignores persistence identity.
 * Do NOT use this for logging or snapshot creation.
 */
internal fun RecipeDraft.toRecipe(): Recipe =
    Recipe(
        id =0L,
    name = name,
    servingsYield = servingsYield,
    totalYieldGrams = totalYieldGrams,
    ingredients = ingredients.map { it.toRecipeIngredient() }
)
internal fun RecipeIngredientDraft.toRecipeIngredient(): RecipeIngredient =
    RecipeIngredient(
        foodId = foodId,
        servings = ingredientServings
    )
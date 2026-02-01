package com.example.adobongkangkong.domain.model
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
data class RecipeDraft(
    val name: String,
    val servingsYield: Double,
    val totalYieldGrams: Double?,
    val ingredients: List<RecipeIngredientDraft>,
)
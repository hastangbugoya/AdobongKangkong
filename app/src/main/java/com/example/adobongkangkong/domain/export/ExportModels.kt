package com.example.adobongkangkong.domain.export

import kotlinx.serialization.Serializable

/**
 * Top-level export bundle stored as JSON files inside a ZIP.
 */
@Serializable
data class ExportManifest(
    val schemaVersion: Int,
    val createdAtIso: String,
    val appName: String,
    val foodsCount: Int,
    val recipesCount: Int,
    val notes: List<String> = emptyList()
)

@Serializable
data class FoodExport(
    val stableId: String,
    val name: String,
    val brand: String? = null,
    val servingSize: Double,
    val servingUnit: String,
    val gramsPerServing: Double? = null,
    val isRecipe: Boolean,
    /**
     * Nutrient values keyed by nutrient code (e.g., "PROTEIN_G", "VITAMIN_C").
     * Value is stored in your DB basis amount (currently nutrientAmountPerBasis).
     */
    val nutrientsByCode: Map<String, Double>
)

@Serializable
data class RecipeExport(
    val stableId: String,
    val name: String,
    val servingsYield: Double,
    val foodStableId: String,
    val ingredients: List<RecipeIngredientExport>
)

@Serializable
data class RecipeIngredientExport(
    /**
     * Stable id of the ingredient food (not the DB row id).
     */
    val ingredientFoodStableId: String,

    /**
     * Stored in your canonical model: ingredient amount in SERVINGS of that ingredient.
     */
    val ingredientServings: Double
)

/**
 * Return value for export run.
 */
data class ExportResult(
    val foodsExported: Int,
    val recipesExported: Int,
    val warnings: List<String>
)

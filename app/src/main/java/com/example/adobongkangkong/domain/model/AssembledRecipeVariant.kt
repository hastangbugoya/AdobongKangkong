package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.FoodEntity

data class AssembledRecipeVariant(
    val recipeFoodId: Long,
    val variantId: Long,
    val variantName: String,
    val variantNotes: String?,
    val finalIngredientLines: List<AssembledRecipeVariantIngredientLine>,
    val removedIngredientLines: List<RemovedRecipeVariantIngredientLine>,
    val warnings: List<String>,
)

data class AssembledRecipeVariantIngredientLine(
    val source: RecipeVariantIngredientLineSource,
    val baseRecipeIngredientId: Long?,
    val food: FoodEntity,
    val servings: Double?,
    val grams: Double?,
    val note: String?,
    val sortOrder: Int,
)

data class RemovedRecipeVariantIngredientLine(
    val baseRecipeIngredientId: Long,
    val food: FoodEntity?,
    val servings: Double?,
    val grams: Double?,
    val note: String?,
)

enum class RecipeVariantIngredientLineSource {
    ORIGINAL,
    ADJUSTED,
    ADDED,
}
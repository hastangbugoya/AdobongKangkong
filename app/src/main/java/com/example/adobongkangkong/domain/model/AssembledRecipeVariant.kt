package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.FoodEntity

data class AssembledRecipeVariant(
    val recipeFoodId: Long,
    val variantId: Long,
    val recipeName: String = "",
    val variantName: String,
    val variantNotes: String?,
    val baseServingsYield: Double? = null,
    val baseTotalYieldGrams: Double? = null,
    val variantServingsYieldOverride: Double? = null,
    val variantTotalYieldGramsOverride: Double? = null,
    val finalIngredientLines: List<AssembledRecipeVariantIngredientLine>,
    val removedIngredientLines: List<RemovedRecipeVariantIngredientLine>,
    val warnings: List<String>,

    // Optional summary data for the variant editor preview card.
    // These are intentionally nullable because the assembler may not always be able
    // to calculate every value, especially price/cost.
    val basePreview: RecipeVariantPreviewTotals? = null,
    val variantPreview: RecipeVariantPreviewTotals? = null,
)

data class RecipeVariantPreviewTotals(
    val totalYieldGrams: Double? = null,
    val servingsYield: Double? = null,

    val calories: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,

    val estimatedCost: Double? = null,
)

data class AssembledRecipeVariantIngredientLine(
    val source: RecipeVariantIngredientLineSource,
    val baseRecipeIngredientId: Long?,
    val food: FoodEntity,

    // Final amount for this variant. This is what the input box edits.
    val servings: Double?,
    val grams: Double?,

    // Original/base recipe amount. This is what the input label should use as
    // a permanent reference, even after the variant adjustment is saved.
    val originalServings: Double? = null,
    val originalGrams: Double? = null,

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

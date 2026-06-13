package com.example.adobongkangkong.domain.model

data class RecipeVariantNutritionComparison(
    val recipe: RecipeVariantPreviewTotals,
    val variant: RecipeVariantPreviewTotals,
    val delta: RecipeVariantPreviewTotals,
    val warnings: List<String> = emptyList(),
)

fun buildRecipeVariantNutritionComparison(
    recipe: RecipeVariantPreviewTotals,
    variant: RecipeVariantPreviewTotals,
    warnings: List<String> = emptyList(),
): RecipeVariantNutritionComparison {
    return RecipeVariantNutritionComparison(
        recipe = recipe,
        variant = variant,
        delta = RecipeVariantPreviewTotals(
            totalYieldGrams = subtractNullable(
                variant.totalYieldGrams,
                recipe.totalYieldGrams,
            ),
            servingsYield = subtractNullable(
                variant.servingsYield,
                recipe.servingsYield,
            ),
            calories = subtractNullable(
                variant.calories,
                recipe.calories,
            ),
            proteinGrams = subtractNullable(
                variant.proteinGrams,
                recipe.proteinGrams,
            ),
            carbsGrams = subtractNullable(
                variant.carbsGrams,
                recipe.carbsGrams,
            ),
            fatGrams = subtractNullable(
                variant.fatGrams,
                recipe.fatGrams,
            ),
            estimatedCost = subtractNullable(
                variant.estimatedCost,
                recipe.estimatedCost,
            ),
        ),
        warnings = warnings,
    )
}

private fun subtractNullable(
    left: Double?,
    right: Double?,
): Double? {
    return if (left != null && right != null) {
        left - right
    } else {
        null
    }
}

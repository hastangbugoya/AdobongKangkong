package com.example.adobongkangkong.domain.model

data class RecipeVariantMacroComparison(
    val recipe: RecipeMacroPreview = RecipeMacroPreview(),
    val variant: RecipeMacroPreview = RecipeMacroPreview(),
    val delta: RecipeMacroPreview = RecipeMacroPreview(),

    val recipePerServing: RecipeMacroPreview = RecipeMacroPreview(),
    val variantPerServing: RecipeMacroPreview = RecipeMacroPreview(),
    val perServingDelta: RecipeMacroPreview = RecipeMacroPreview(),
    val baseServingsYield: Double? = null,
    val variantServingsYield: Double? = null,
    val variantServingsYieldOverride: Double? = null,

    val warnings: List<String> = emptyList(),
) {
    val hasServingYieldOverride: Boolean
        get() {
            val base = baseServingsYield ?: return false
            val variant = variantServingsYield ?: return false
            return kotlin.math.abs(base - variant) > 0.0001
        }
}

fun RecipeMacroPreview.minus(
    other: RecipeMacroPreview,
): RecipeMacroPreview {
    return RecipeMacroPreview(
        totalCalories = totalCalories - other.totalCalories,
        totalProteinG = totalProteinG - other.totalProteinG,
        totalCarbsG = totalCarbsG - other.totalCarbsG,
        totalFatG = totalFatG - other.totalFatG,
    )
}

fun RecipeMacroPreview.dividedBy(
    divisor: Double?,
): RecipeMacroPreview {
    val safeDivisor = divisor?.takeIf { it > 0.0 } ?: return RecipeMacroPreview()

    return RecipeMacroPreview(
        totalCalories = totalCalories / safeDivisor,
        totalProteinG = totalProteinG / safeDivisor,
        totalCarbsG = totalCarbsG / safeDivisor,
        totalFatG = totalFatG / safeDivisor,
    )
}

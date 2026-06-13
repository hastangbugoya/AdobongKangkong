package com.example.adobongkangkong.domain.model

data class RecipeVariantMacroComparison(
    val recipe: RecipeMacroPreview = RecipeMacroPreview(),
    val variant: RecipeMacroPreview = RecipeMacroPreview(),
    val delta: RecipeMacroPreview = RecipeMacroPreview(),
    val warnings: List<String> = emptyList(),
)

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

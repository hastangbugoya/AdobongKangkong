package com.example.adobongkangkong.domain.model

data class RecipeMacroPreview(
    val totalCalories: Double = 0.0,
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0
) {
    fun perServing(servingsYield: Double): RecipeMacroPreview {
        if (servingsYield <= 0.0) return RecipeMacroPreview()
        return RecipeMacroPreview(
            totalCalories / servingsYield,
            totalProteinG / servingsYield,
            totalCarbsG / servingsYield,
            totalFatG / servingsYield
        )
    }
}

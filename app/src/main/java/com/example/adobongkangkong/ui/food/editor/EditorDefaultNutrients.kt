package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.domain.nutrition.NutrientCodes

object EditorDefaultNutrients {

    data class Spec(
        val code: String,
        val displayName: String,
        val searchQuery: String = displayName
    )

    val defaults: List<Spec> = listOf(
        Spec(
            code = NutrientCodes.CALORIES_KCAL,
            displayName = "Calories"
        ),
        Spec(
            code = NutrientCodes.PROTEIN_G,
            displayName = "Protein"
        ),
        Spec(
            code = NutrientCodes.CARBS_G,
            displayName = "Carbohydrates",
            searchQuery = "Carbohydrates"
        ),
        Spec(
            code = NutrientCodes.FAT_G,
            displayName = "Fat"
        ),
        Spec(
            code = NutrientCodes.SUGARS_G,
            displayName = "Sugars",
            searchQuery = "Sugars"
        ),
        Spec(
            code = NutrientCodes.FIBER_G,
            displayName = "Fiber"
        ),
        Spec(
            code = NutrientCodes.SODIUM_MG,
            displayName = "Sodium"
        ),
    )

    val codes: Set<String> = defaults.map { it.code }.toSet()

    fun rankFor(code: String): Int =
        defaults.indexOfFirst { it.code == code }.let { if (it >= 0) it else Int.MAX_VALUE }
}
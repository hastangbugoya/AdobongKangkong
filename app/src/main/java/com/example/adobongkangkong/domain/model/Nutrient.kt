package com.example.adobongkangkong.domain.model

data class Nutrient (
    val id: Long = 0,
    val code: String, // e.g. "CALORIES", "PROTEIN", "VITAMIN_C"
    val displayName: String, // e.g. "Calories", "Protein", "Vitamin C"
    val unit: NutrientUnit = NutrientUnit.G, // "kcal", "g", "mg", "µg"
    val category: NutrientCategory = NutrientCategory.MACRO // "macro", "vitamin", "mineral", "other"
)
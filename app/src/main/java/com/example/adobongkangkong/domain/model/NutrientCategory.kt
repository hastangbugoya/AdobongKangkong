package com.example.adobongkangkong.domain.model

enum class NutrientCategory(
    val dbValue: String,
    val displayName: String,
    val sortOrder: Int
) {
    // Energy & Macros
    ENERGY("energy", "Energy", 0),
    MACRO("macro", "Macronutrients", 1),

    // Fats (detailed breakdown)
    FAT("fat", "Fats", 2),
    FATTY_ACID("fatty_acid", "Fatty Acids", 3),

    // Carbohydrates breakdown
    CARBOHYDRATE("carbohydrate", "Carbohydrates", 4),
    SUGAR("sugar", "Sugars", 5),
    FIBER("fiber", "Fiber", 6),

    // Protein breakdown
    PROTEIN("protein", "Protein", 7),
    AMINO_ACID("amino_acid", "Amino Acids", 8),

    // Vitamins
    VITAMIN("vitamin", "Vitamins", 10),

    // Minerals
    MINERAL("mineral", "Minerals", 11),

    // Other important nutrition data
    STEROL("sterol", "Sterols", 12),           // cholesterol, phytosterols
    ELECTROLYTE("electrolyte", "Electrolytes", 13),
    PHYTONUTRIENT("phytonutrient", "Phytonutrients", 14),
    ANTIOXIDANT("antioxidant", "Antioxidants", 15),

    // Misc / fallback
    OTHER("other", "Other", 99);

    companion object {
        fun fromDb(value: String): NutrientCategory =
            entries.firstOrNull { it.dbValue == value } ?: OTHER
    }
}

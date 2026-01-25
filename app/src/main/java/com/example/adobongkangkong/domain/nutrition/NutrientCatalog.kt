package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit

/**
 * Canonical list of nutrients your app “knows about”.
 *
 * This is the stable source of truth for:
 * - code (stable identifier, used for imports + lookups)
 * - display name (default UI label)
 * - unit/category defaults
 * - built-in aliases for search
 *
 * IMPORTANT:
 * - The DB remains editable (user can change display name, etc.) if you want.
 * - Sync policy decides which fields we override.
 */
object NutrientCatalog {

    data class Entry(
        val code: String,
        val displayName: String,
        val unit: NutrientUnit,
        val category: NutrientCategory,
        val aliases: List<String> = emptyList()
    )

    /**
     * Expand this list over time.
     * Keep codes stable once shipped.
     */
    val entries: List<Entry> = listOf(
        Entry(
            code = "CALORIES",
            displayName = "Calories",
            unit = NutrientUnit.KCAL,
            category = NutrientCategory.MACRO,
            aliases = listOf("kcal", "calories", "energy")
        ),
        Entry(
            code = "PROTEIN",
            displayName = "Protein",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("protein", "prot")
        ),
        Entry(
            code = "CARBS",
            displayName = "Carbs",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("carb", "carbs", "carbohydrate", "carbohydrates")
        ),
        Entry(
            code = "FAT",
            displayName = "Fat",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("fat", "total fat", "lipid")
        ),

        // Examples – add the rest as needed:
        Entry(
            code = "VITAMIN_C",
            displayName = "Vitamin C",
            unit = NutrientUnit.MG,
            category = NutrientCategory.VITAMIN,
            aliases = listOf("vit c", "ascorbic acid")
        ),
        Entry(
            code = "SODIUM",
            displayName = "Sodium",
            unit = NutrientUnit.MG,
            category = NutrientCategory.MINERAL,
            aliases = listOf("na", "salt")
        )
    )
}

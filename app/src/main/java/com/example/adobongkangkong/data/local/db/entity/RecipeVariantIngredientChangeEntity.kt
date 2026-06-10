package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object RecipeVariantIngredientChangeType {
    const val ADD = "ADD"
    const val REMOVE = "REMOVE"
    const val ADJUST = "ADJUST"

    val validValues: Set<String> = setOf(
        ADD,
        REMOVE,
        ADJUST,
    )
}

/**
 * Ingredient delta for a recipe variant.
 *
 * Mental model:
 * final ingredients =
 *     original recipe ingredients
 *     - removed original lines
 *     + adjusted original lines
 *     + added new lines
 *
 * Important:
 * - baseRecipeIngredientId points to the original recipe ingredient line.
 * - Do not key adjustments/removals by foodId because the same food can appear
 *   more than once in a recipe.
 */
@Entity(
    tableName = "recipe_variant_ingredient_change",
    indices = [
        Index("variantId"),
        Index("baseRecipeIngredientId"),
        Index("foodId"),
    ],
)
data class RecipeVariantIngredientChangeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val variantId: Long,

    /**
     * Use RecipeVariantIngredientChangeType.ADD / REMOVE / ADJUST.
     *
     * Stored as String to avoid adding Room enum converters in this first pass.
     */
    val changeType: String,

    /**
     * Used by REMOVE and ADJUST.
     *
     * This refers to the original recipe ingredient line id.
     */
    val baseRecipeIngredientId: Long? = null,

    /**
     * Used by ADD.
     */
    val foodId: Long? = null,

    /**
     * Same idea as RecipeIngredientEntity:
     * either servings or grams should be supplied, not both.
     */
    val servings: Double? = null,
    val grams: Double? = null,

    val note: String? = null,

    /**
     * Future display ordering for added lines.
     */
    val sortOrder: Int = 0,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
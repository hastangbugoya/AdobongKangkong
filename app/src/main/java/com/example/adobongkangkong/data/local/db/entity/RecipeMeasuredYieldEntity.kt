package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the current measured cooked yield used for gram-based logging of a recipe
 * or recipe variant.
 *
 * This is intentionally not full cooked-batch tracking.
 *
 * It does not represent:
 * - inventory
 * - remaining grams
 * - expiration
 * - whether a physical batch is still available
 *
 * It only stores the active yield assumption used to convert:
 *
 * grams eaten -> fraction of recipe -> serving-equivalent nutrient scaling
 *
 * Recipe variants handle ingredient/nutrition mutations.
 * Measured yield handles gram scaling.
 * Cooked batch logging remains shelved.
 *
 * UX rule:
 * Whenever the app gives the user an option to log a recipe or recipe variant by
 * grams, the UI must show the active yield and when it was last updated.
 */
@Entity(
    tableName = "recipe_measured_yields",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recipeId"),
        Index("variantId"),
        Index(value = ["recipeId", "variantId"])
    ]
)
data class RecipeMeasuredYieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val recipeId: Long,

    /**
     * Null means this yield belongs to the base recipe.
     * Non-null means this yield belongs to a specific recipe variant.
     */
    val variantId: Long? = null,

    /**
     * Final measured cooked yield in grams.
     */
    val yieldGrams: Double,

    /**
     * When this measured yield was entered or last confirmed by the user.
     *
     * This timestamp must be shown anywhere the user can use grams to log the
     * recipe or variant.
     */
    val updatedAtEpochMs: Long,

    /**
     * Optional user note, such as "large pot", "air fryer", "less water", etc.
     */
    val note: String? = null,

    /**
     * Active-row flag.
     *
     * Only one active row should be used for a given recipeId + variantId pair.
     * This is enforced by DAO transaction logic rather than a UNIQUE index because
     * SQLite UNIQUE indexes do not treat NULL variantId values the way we need for
     * base recipes.
     */
    val isActive: Boolean = true
)
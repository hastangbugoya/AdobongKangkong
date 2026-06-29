package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Template items for a planned_series.
 *
 * These represent the "what should be in this recurring meal" blueprint.
 * They are NOT tied to a specific planned_meal row.
 *
 * When EnsureSeriesOccurrencesWithinHorizonUseCase creates a new planned_meal
 * occurrence, these rows are copied into planned_items for that meal.
 *
 * --------------------------------------------
 * FOOD / RECIPE / VARIANT HANDLING MODEL
 * --------------------------------------------
 *
 * Exactly one of:
 *
 *   - foodId
 *   - recipeId
 *
 * should be non-null.
 *
 * 1) Food (foodId != null)
 *    - References a concrete FoodEntity.
 *    - When an occurrence is created, this is copied into planned_items as:
 *      type = FOOD, refId = foodId, recipeVariantId = null.
 *
 * 2) Recipe (recipeId != null)
 *    - References a RecipeEntity logical recipe definition.
 *    - recipeVariantId may optionally point to a RecipeVariantEntity.
 *    - recipeVariantId == null means use the base recipe.
 *    - recipeVariantId != null means future generated occurrences should use that
 *      selected variant for display, planner nutrition preview, and log snapshot creation.
 *
 * 3) Recipe Batch
 *    - RECIPE_BATCH is intentionally NOT stored at the recurring template layer.
 *    - Cooked/prepared batch planning is currently paused/shelved.
 *    - Do not expand batch behavior here unless the batch concept is deliberately revived.
 *
 * Rationale:
 * - Series templates represent intent ("have chili every Monday").
 * - Recipe variants represent planned recipe modifications ("less oil version").
 * - Existing generated meals are never overwritten; variant changes to a template only affect
 *   future occurrences that have not yet been materialized.
 *
 * --------------------------------------------
 * Quantity Model
 * --------------------------------------------
 *
 * grams and servings mirror PlannedItemEntity.
 * At least one should be non-null.
 *
 * Interpretation rules are identical to planned_items.
 *
 * --------------------------------------------
 * Ordering
 * --------------------------------------------
 *
 * sortOrder preserves stable ordering when items are copied
 * into newly created planned_meals.
 *
 * --------------------------------------------
 * Idempotency Guarantee
 * --------------------------------------------
 *
 * These rows are copied only when a new planned_meal occurrence
 * is created. Existing meals are never overwritten.
 *
 * Deleting a series cascades and deletes these template rows.
 */
@Entity(
    tableName = "planned_series_items",
    foreignKeys = [
        ForeignKey(
            entity = PlannedSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["seriesId", "sortOrder", "id"]),
        Index(value = ["recipeVariantId"])
    ]
)
data class PlannedSeriesItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val seriesId: Long,

    // Mirror planned_items “what is this item?” fields.
    // Keep as nullable longs: exactly one should be non-null.
    val foodId: Long? = null,
    val recipeId: Long? = null,

    /**
     * Optional recipe variant selected for this recurring recipe template item.
     *
     * Valid only when recipeId != null and foodId == null.
     * Null means the base recipe.
     *
     * When this template creates a concrete planned item, this value must be copied into
     * PlannedItemEntity.recipeVariantId.
     */
    val recipeVariantId: Long? = null,

    // Quantity (mirror planned_items)
    val grams: Double? = null,
    val servings: Double? = null,

    // Optional free text note (prep instruction, brand preference, etc.)
    val note: String? = null,

    // Stable ordering inside the meal once copied.
    val sortOrder: Int = Int.MAX_VALUE
)
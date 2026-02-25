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
 * FOOD / RECIPE / RECIPE-BATCH HANDLING MODEL
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
 *    - When occurrence is created, this is copied directly into planned_items.
 *
 * 2) Recipe (recipeId != null)
 *    - References a Recipe (logical definition).
 *    - At logging time, the user must select or create a RecipeBatch.
 *    - Recipe batches produce a snapshot Food (immutable nutrition at cook time).
 *
 * 3) Recipe Batch (NOT stored here)
 *    - We do NOT store recipeBatchId at the series template layer.
 *    - Batches are runtime constructs created when cooking.
 *    - Logging resolves recipe → batch → snapshot food.
 *
 * Rationale:
 * - Series templates represent intent ("have chili every Monday").
 * - Batches represent cooked instances ("this specific pot of chili").
 * - Batches must not be pre-baked into recurrence rules.
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
        Index(value = ["seriesId", "sortOrder", "id"])
    ]
)
data class PlannedSeriesItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val seriesId: Long,

    // Mirror planned_items “what is this item?” fields.
    // Keep as nullable longs (typical pattern: exactly one is non-null).
    val foodId: Long? = null,
    val recipeId: Long? = null,

    // Quantity (mirror planned_items)
    val grams: Double? = null,
    val servings: Double? = null,

    // Optional free text note (prep instruction, brand preference, etc.)
    val note: String? = null,

    // Stable ordering inside the meal once copied.
    val sortOrder: Int = Int.MAX_VALUE
)

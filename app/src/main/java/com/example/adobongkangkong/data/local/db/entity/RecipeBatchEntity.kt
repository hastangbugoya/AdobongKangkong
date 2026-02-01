package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A specific cooking event (batch) created from an editable recipe.
 *
 * Key rule:
 * - The recipe definition remains editable.
 * - Creating a batch creates a *new Food snapshot* ([batchFoodId]) that is loggable.
 * - Logs should reference the batch food snapshot, not the recipe.
 */
@Entity(
    tableName = "recipe_batches",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["createdAt"]),
        Index(value = ["batchFoodId"], unique = true)
    ]
)
data class RecipeBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val recipeId: Long,

    /**
     * The loggable Food snapshot created for this batch.
     * This is the Food ID that should be logged in DayLog/QuickAdd.
     */
    val batchFoodId: Long,

    /** Final cooked batch weight in grams (measured). */
    val cookedYieldGrams: Double,

    /**
     * Servings yield USED for this batch (optional).
     * If null, you can fall back to recipe.servingsYield when logging "by serving".
     */
    val servingsYieldUsed: Double? = null,

    val createdAt: Instant = Instant.now()
)


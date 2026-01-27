package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A specific cooking event for a recipe.
 *
 * Why this exists:
 * - A recipe definition is editable.
 * - Cooked yield varies each time you cook.
 * - Logging by cooked grams MUST reference a batch/yield context.
 */
@Entity(
    tableName = "recipe_batches",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["createdAt"])
    ]
)
data class RecipeBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val recipeId: Long,

    /** Final cooked batch weight in grams (measured). */
    val cookedYieldGrams: Double,

    /**
     * Servings yield USED for this batch (optional).
     * If null, you can fall back to recipe.servingsYield when logging "by serving".
     */
    val servingsYieldUsed: Double? = null,

    val createdAt: Instant = Instant.now()
)

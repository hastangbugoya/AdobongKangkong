package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["foodId"]),
        Index(value = ["isDeleted"])
    ]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Stable identifier used for export/import reconciliation.
     * This must never change once created.
     */
    val stableId: String = UUID.randomUUID().toString(),

    // the Food row that represents this recipe for logging/search
    val foodId: Long,

    val name: String,

    // how many servings a batch yields (portions)
    val servingsYield: Double,

    /**
     * Final cooked batch weight in grams.
     * Used to support logging by grams.
     */
    val totalYieldGrams: Double? = null,

    /**
     * Optional notes for the recipe.
     *
     * - User-entered free text
     * - Not used for computation
     * - Safe to be null for all existing rows
     */
    val notes: String? = null,

    /**
     * Soft delete flag.
     *
     * - Default recipe delete should set this to true and hide the recipe from
     *   normal recipe lists/pickers.
     * - Historical references should remain valid.
     * - Backing Food soft delete is coordinated separately by recipe delete flow;
     *   this field exists so RecipeEntity retains its own lifecycle state.
     */
    val isDeleted: Boolean = false,

    /**
     * Optional deletion timestamp for debugging / future "Recently deleted" UI.
     * Stored as epoch millis (nullable).
     */
    val deletedAtEpochMs: Long? = null,

    val createdAt: Instant = Instant.now()
)
package com.example.adobongkangkong.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Saved recipe-scoped variant.
 *
 * A variant is NOT a normal FoodEntity and should not appear in the global food picker.
 * It belongs to one base recipe and stores metadata plus optional frozen nutrition.
 *
 * V1 purpose:
 * - show/manage variants from Recipe Builder later
 * - select a variant after choosing the base recipe during logging later
 * - preserve future room for yield overrides and frozen nutrient snapshots
 */
@Entity(
    tableName = "recipe_variant",
    indices = [
        Index("recipeFoodId"),
        Index(value = ["recipeFoodId", "name"], unique = true),
    ],
)
data class RecipeVariantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * The FoodEntity id of the base recipe food.
     *
     * AK recipes are represented/logged through Food rows, so this keeps the
     * variant attached to the recipe without making the variant itself a FoodEntity.
     */
    val recipeFoodId: Long,

    val name: String,

    val notes: String? = null,

    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,

    /**
     * Optional future support.
     *
     * Null means: use the base recipe yield/serving behavior.
     */
    val servingsYieldOverride: Double? = null,

    /**
     * Optional future support.
     *
     * Null means: use the base recipe total yield behavior.
     */
    val totalYieldGramsOverride: Double? = null,

    /**
     * Optional frozen computed nutrients for this variant.
     *
     * Deltas explain the variant.
     * This snapshot preserves the final computed nutrition.
     */
    val nutrientsJsonSnapshot: String? = null,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

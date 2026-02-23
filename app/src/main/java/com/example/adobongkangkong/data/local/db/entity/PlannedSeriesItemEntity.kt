package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Template items for a planned_series.
 *
 * This mirrors PlannedItemEntity where appropriate, but does NOT reference planned_meals.
 * These are copied into planned_items ONLY when a new meal occurrence is created.
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

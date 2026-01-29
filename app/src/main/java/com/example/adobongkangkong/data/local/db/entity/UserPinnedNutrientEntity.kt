package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores user-selected nutrients pinned to the dashboard.
 *
 * - Primary key on [nutrientCode] ensures the same nutrient cannot be pinned twice.
 * - [position] keeps a stable ordering (slot 0 and slot 1).
 *
 * We enforce "max 2 pinned" in the domain use case (not via DB constraints),
 * because it's a UX rule that may evolve.
 */
@Entity(
    tableName = "user_pinned_nutrients",
    indices = [
        Index(value = ["nutrientCode"])
    ]
)
data class UserPinnedNutrientEntity(
    /** 0 or 1 (unique), used to preserve slot ordering. */
    @PrimaryKey
    val position: Int,
    val nutrientCode: String
)


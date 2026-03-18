package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the user-selected nutrient codes used to evaluate monthly calendar day success.
 *
 * Scope guard:
 * - Calendar-only preference table.
 * - Separate from dashboard pinned nutrients / critical nutrients.
 * - Does NOT change nutrient targets or shared domain nutrient status computation.
 *
 * Table shape:
 * - One row per selected nutrient code.
 * - Presence of a row means the nutrient is selected for calendar success evaluation.
 * - Empty table means "no explicit selection", so calendar UI can fall back to current behavior.
 */
@Entity(
    tableName = "calendar_success_nutrients",
    indices = [
        Index(value = ["nutrientCode"])
    ]
)
data class CalendarSuccessNutrientEntity(
    @PrimaryKey
    val nutrientCode: String
)

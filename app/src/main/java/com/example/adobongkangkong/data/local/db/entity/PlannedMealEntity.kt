package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_meals",
    indices = [
        Index(value = ["date"]),
        Index(value = ["date", "sortOrder"])
    ]
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Stored as yyyy-MM-dd */
    val date: String,

    /** BREAKFAST | LUNCH | DINNER | SNACK | CUSTOM */
    val slot: MealSlot,

    /** Only meaningful when slot == CUSTOM */
    val customLabel: String? = null,

    val nameOverride: String? = null,

    /** Order within the day */
    val sortOrder: Int,

    /**
     * Recurrence series linkage (nullable means one-off planned meal).
     * This is the “occurrence layer” field for Option 1.
     */
    val seriesId: Long? = null,

    /**
     * Occurrence lifecycle status.
     * Stored as TEXT using enum.name (no type converter required).
     */
    val status: String = PlannedOccurrenceStatus.ACTIVE.name,

    /** When non-null, this planned meal occurrence has already been logged. */
    val loggedAtEpochMs: Long? = null
)

enum class PlannedOccurrenceStatus {
    ACTIVE,
    CANCELLED,
    OVERRIDDEN
}

enum class MealSlot(
    val display: String
){
    BREAKFAST("Breakast"),
    LUNCH("Lunch"),
    DINNER(display = "Dinner"),
    SNACK("Snack"),
    CUSTOM("Custom")
}
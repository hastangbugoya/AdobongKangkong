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
    val display: String,
    val sortOrder: Int
) {
    BREAKFAST("Breakfast", 10),
    AM_SNACK("AM Snack", 20),
    LUNCH("Lunch", 30),
    PM_SNACK("PM Snack", 40),
    DINNER("Dinner", 50),
    BEDTIME("Bedtime", 60),
    PRE_WORKOUT("Pre-Workout", 70),
    SNACK("Snack", 80),
    INTRA_WORKOUT("Intra-Workout", 90),
    POST_WORKOUT("Post-Workout", 100),
    CUSTOM("Custom", 110);

    companion object {
        fun sortedEntries(): List<MealSlot> =
            entries.sortedBy { it.sortOrder }
    }
}
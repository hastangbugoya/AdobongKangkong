package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_series",
    indices = [
        Index(value = ["effectiveStartDate"]),
        Index(value = ["effectiveEndDate"])
    ]
)
data class PlannedSeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** ISO yyyy-MM-dd */
    val effectiveStartDate: String,

    /** ISO yyyy-MM-dd (nullable) */
    val effectiveEndDate: String? = null,

    /**
     * Recurrence end-condition mode for this series.
     *
     * Room supports enum persistence via enum-to-string conversion, so this can remain
     * strongly typed in Kotlin while still being stored in a single database column.
     */
    val endConditionType: PlannedSeriesEndConditionType,

    /**
     * For UNTIL_DATE: ISO yyyy-MM-dd
     * For REPEAT_COUNT: integer string
     * For INDEFINITE: null
     */
    val endConditionValue: String? = null,

    /**
     * Optional provenance: which planned_meal was used as the template source
     * (NOT a foreign key; series must survive if meal is deleted).
     */
    val sourceMealId: Long? = null,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class PlannedSeriesEndConditionType {
    UNTIL_DATE,
    REPEAT_COUNT,
    INDEFINITE
}
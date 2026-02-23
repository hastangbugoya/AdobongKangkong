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
     * UNTIL_DATE | REPEAT_COUNT | INDEFINITE
     * Stored as TEXT to avoid adding converters in Phase 0.
     */
    val endConditionType: String,

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

object PlannedSeriesEndConditionType {
    const val UNTIL_DATE = "UNTIL_DATE"
    const val REPEAT_COUNT = "REPEAT_COUNT"
    const val INDEFINITE = "INDEFINITE"
}
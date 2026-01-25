package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_issues",
    indices = [
        Index("runId"),
        Index("foodId"),
        Index("severity")
    ]
)
data class ImportIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val runId: Long,

    /** "WARNING" or "ERROR" (string keeps it migration-friendly) */
    val severity: String,

    /** ImportIssueCode name, e.g. "MISSING_GRAMS_PER_SERVING_FOR_VOLUME" */
    val code: String,

    /** CSV row number (match your loop index) */
    val rowIndex: Int,

    /** Optional CSV column name like "Weight" or nutrient header */
    val field: String?,

    val message: String,

    /** Raw value from CSV cell (optional) */
    val rawValue: String?,

    /**
     * Optional link to the inserted/updated food row when we can determine it.
     * Enables "Fix now" deep links.
     */
    val foodId: Long?
)

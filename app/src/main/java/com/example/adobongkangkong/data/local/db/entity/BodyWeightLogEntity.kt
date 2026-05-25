package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted body-weight log for long-term weight trend tracking.
 *
 * MVP rule:
 * - One body-weight entry per calendar date.
 * - `dateIso` is the stable day key, formatted yyyy-MM-dd.
 * - Updating a day's weight should replace/update that day's row rather than creating duplicates.
 *
 * Why this is Room data:
 * - Weight logs are historical user records.
 * - They need time-series queries.
 * - They should survive app restarts/backups/restores.
 *
 * Why reminder settings are NOT here:
 * - Reminder mode, interval, and dismiss/reset anchor are preferences.
 * - Those belong in UserPreferences/DataStore.
 */
@Entity(
    tableName = "body_weight_logs",
    indices = [
        Index(
            value = ["dateIso"],
            unique = true
        )
    ]
)
data class BodyWeightLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Calendar day this weight belongs to.
     *
     * Format: yyyy-MM-dd
     */
    val dateIso: String,

    /**
     * Stored numeric weight value in the selected unit.
     *
     * Example:
     * - 188.4 with unit "LB"
     * - 85.5 with unit "KG"
     */
    val weight: Double,

    /**
     * Stored unit code.
     *
     * MVP supported values:
     * - "LB"
     * - "KG"
     *
     * Kept as String to avoid a Room type converter for this tiny table.
     */
    val unit: String,

    /**
     * Optional user note for context.
     *
     * Examples:
     * - "Morning weigh-in"
     * - "After workout"
     * - "Travel week"
     */
    val note: String? = null,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
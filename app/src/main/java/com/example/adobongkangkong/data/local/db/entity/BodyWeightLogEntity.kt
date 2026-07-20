package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted daily body-weight value used by AK's long-term trend screens and
 * dashboard weight-reminder logic.
 *
 * This table intentionally remains one row per calendar date. Multiple scale
 * readings for the same day belong in BodyWeightMeasurementEntity; this row is
 * the selected daily trend value derived from those raw measurements or entered
 * manually by the user.
 *
 * MVP rule:
 * - One official trend weight per calendar date.
 * - `dateIso` is the stable day key, formatted yyyy-MM-dd.
 * - Updating a day's trend weight should replace/update that day's row rather
 *   than creating duplicate trend rows.
 *
 * Future selection support:
 * - `selectedMeasurementId` may point to the raw measurement used for this
 *   daily trend value.
 * - `trendSelectionMethod` records the rule that selected it, such as closest
 *   to preferred weigh-in time or manual selection.
 * - `isTrendUserOverride` lets AK preserve a user-picked daily trend value even
 *   if the default selection rule changes later.
 *
 * Why reminder settings are NOT here:
 * - Reminder mode, interval, dismiss/reset anchor, preferred weigh-in time, and
 *   default trend-selection method are preferences.
 * - Those belong in UserPreferences/DataStore, not in this historical row.
 */
@Entity(
    tableName = "body_weight_logs",
    indices = [
        Index(
            value = ["dateIso"],
            unique = true
        ),
        Index(value = ["selectedMeasurementId"])
    ]
)
data class BodyWeightLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Calendar day this official trend weight belongs to.
     *
     * Format: yyyy-MM-dd
     */
    val dateIso: String,

    /**
     * Stored numeric trend weight value in the selected display unit.
     *
     * Example:
     * - 188.4 with unit "LB"
     * - 85.5 with unit "KG"
     */
    val weight: Double,

    /**
     * Stored display unit code.
     *
     * MVP supported values:
     * - "LB"
     * - "KG"
     *
     * Kept as String to avoid a Room type converter for this tiny table.
     */
    val unit: String,

    /**
     * Raw measurement chosen as this day's trend value, when the value came
     * from BodyWeightMeasurementEntity.
     *
     * This is nullable for legacy rows and direct manual entries that have not
     * yet been mirrored into the raw-measurement table.
     */
    val selectedMeasurementId: Long? = null,

    /**
     * Rule that produced this daily trend value.
     *
     * Suggested values are defined in BodyWeightTrendSelectionMethod. Nullable
     * keeps old rows migration-friendly.
     */
    val trendSelectionMethod: String? = null,

    /**
     * True when the user explicitly chose this day’s trend reading.
     *
     * Automatic re-selection should not replace user overrides unless the user
     * asks AK to recalculate the day.
     */
    val isTrendUserOverride: Boolean = false,

    /**
     * When this trend selection was made or refreshed.
     */
    val trendSelectedAtEpochMs: Long? = null,

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

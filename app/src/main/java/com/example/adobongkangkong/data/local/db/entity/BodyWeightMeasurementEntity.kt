package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw body-weight measurement captured by AK.
 *
 * This table may contain multiple rows for the same calendar date. It exists so
 * AK can preserve same-day scale readings such as morning, after-workout, and
 * before-bed weights without confusing them with the single daily trend value in
 * BodyWeightLogEntity.
 *
 * MVP import rule:
 * - Keep imported Health Connect weights only when they are not near-duplicates.
 * - Same-day imported readings should usually be at least the configured
 *   minimum gap apart, defaulting to four hours.
 * - Very close readings from the same source, within a short duplicate window,
 *   should be treated as the same scale measurement.
 *
 * Long-term trend rule:
 * - Reports should not blindly use every row here.
 * - AK should derive or store one selected daily trend value in
 *   BodyWeightLogEntity.
 */
@Entity(
    tableName = "body_weight_measurements",
    indices = [
        Index(value = ["dateIso"]),
        Index(value = ["measuredAtEpochMs"]),
        Index(value = ["dateIso", "measuredAtEpochMs"]),
        Index(value = ["source", "sourceRecordId"], unique = true)
    ]
)
data class BodyWeightMeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Local calendar date for grouping same-day measurements.
     *
     * Format: yyyy-MM-dd
     */
    val dateIso: String,

    /**
     * Original measurement time.
     *
     * For Health Connect imports, this should be WeightRecord.time converted to
     * epoch milliseconds.
     */
    val measuredAtEpochMs: Long,

    /**
     * Canonical stored body weight.
     *
     * Raw measurements use kilograms so AK can compare readings from different
     * sources without depending on the user's current display unit.
     */
    val weightKg: Double,

    /**
     * Measurement source.
     *
     * Suggested values are defined in BodyWeightMeasurementSource.
     */
    val source: String,

    /**
     * Health Connect data origin package or other source package, if known.
     *
     * Examples may include a Renpho package, Google Fit, Samsung Health, or
     * another app writing WeightRecord data to Health Connect.
     */
    val sourcePackage: String? = null,

    /**
     * Stable source record id when available.
     *
     * For Health Connect, use metadata id when the client version exposes it.
     * When unavailable, AK should rely on sourcePackage + measuredAt + weightKg
     * duplicate checks.
     */
    val sourceRecordId: String? = null,

    /**
     * Time AK imported the source measurement.
     *
     * Null for direct manual measurements entered inside AK.
     */
    val importedAtEpochMs: Long? = null,

    /**
     * Optional user note for context.
     */
    val note: String? = null,

    /**
     * Soft-delete marker so historical imports can be hidden without losing the
     * ability to audit why a trend value changed.
     */
    val isDeleted: Boolean = false,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

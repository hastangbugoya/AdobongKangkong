package com.example.adobongkangkong.domain.weight

import java.time.LocalDate

/**
 * Domain model for one official daily body-weight trend log.
 *
 * Data rules:
 * - One body-weight log per calendar date.
 * - Logging weight for an existing date updates that date's row.
 * - This model represents the value AK uses for daily trends, reminders, and
 *   weight charts.
 *
 * Multiple-read support:
 * - Raw same-day readings/imports live in BodyWeightMeasurement.
 * - selectedMeasurementId optionally points to the raw measurement currently
 *   used as this day's official trend value.
 * - trendSelectionMethod records how AK chose that daily trend value so future
 *   user settings can support choices such as closest-to-morning-time, latest,
 *   average, or manual selection.
 *
 * This model is intentionally separate from dashboard reminder settings.
 * Reminder mode/interval/dismiss state belongs in UserPreferences/DataStore.
 */
data class BodyWeightLog(
    val id: Long = 0L,
    val date: LocalDate,
    val weight: Double,
    val unit: BodyWeightUnit,
    val note: String? = null,

    /**
     * Raw measurement row used for this daily trend value, when known.
     *
     * Existing legacy rows may have null until they are backfilled or replaced.
     */
    val selectedMeasurementId: Long? = null,

    /**
     * How the selected measurement was chosen.
     *
     * Null is allowed for older rows created before raw measurement support.
     */
    val trendSelectionMethod: BodyWeightTrendSelectionMethod? = null,

    /**
     * True when the user explicitly picked this value for the day's trend.
     *
     * False means the value was chosen by AK's default rule or came from older
     * daily-only logging behavior.
     */
    val isTrendUserOverride: Boolean = false,

    /**
     * Epoch millis when this daily trend selection was made or refreshed.
     */
    val trendSelectedAtEpochMs: Long? = null,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/**
 * Supported body-weight units.
 *
 * Stored as stable string codes in Room through BodyWeightLogEntity.unit.
 */
enum class BodyWeightUnit(
    val code: String,
    val symbol: String
) {
    LB("LB", "lb"),
    KG("KG", "kg");

    companion object {
        fun fromCode(code: String): BodyWeightUnit =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: LB
    }
}

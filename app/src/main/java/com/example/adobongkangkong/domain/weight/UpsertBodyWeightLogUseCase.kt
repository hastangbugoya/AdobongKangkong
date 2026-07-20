package com.example.adobongkangkong.domain.weight

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Saves the user's official daily body-weight trend value.
 *
 * AK now separates daily trend weight from raw measurements:
 * - BodyWeightMeasurement stores the raw reading that was entered or imported.
 * - BodyWeightLog stores the one official daily trend value used by existing
 *   dashboard, reminder, and chart flows.
 *
 * Manual save behavior:
 * - A manual entry creates a raw BodyWeightMeasurement with source MANUAL.
 * - The daily BodyWeightLog is then upserted and linked to that measurement.
 * - Manual saves are treated as user-selected trend values so future automatic
 *   trend-selection rules do not silently replace them.
 *
 * Health Connect import behavior should live in a separate import use case.
 * Import-specific duplicate checks and the four-hour same-day gap rule should
 * not block intentional manual entries.
 *
 * Reminder rule:
 * - A successful manual save resets weightLogLastPromptResetEpochDay to the
 *   logged date's epoch day.
 */
class UpsertBodyWeightLogUseCase @Inject constructor(
    private val repository: BodyWeightLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val zoneId: ZoneId
) {

    suspend operator fun invoke(
        date: LocalDate,
        weight: Double,
        unit: BodyWeightUnit,
        note: String? = null
    ): Result {
        if (weight <= 0.0) {
            return Result.Error("Weight must be greater than 0.")
        }

        val now = Instant.now()
        val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
        val measuredAt = manualMeasurementInstantFor(
            date = date,
            now = now,
            zoneId = zoneId
        )

        val measurementId = repository.insertMeasurement(
            BodyWeightMeasurement(
                date = date,
                measuredAt = measuredAt,
                weightKg = weight.toKilograms(unit),
                source = BodyWeightMeasurementSource.MANUAL,
                sourcePackage = null,
                sourceRecordId = null,
                importedAt = null,
                note = cleanNote,
                isDeleted = false,
                createdAt = now,
                updatedAt = now
            )
        )

        val logId = repository.upsertByDate(
            date = date,
            weight = weight,
            unit = unit,
            note = cleanNote,
            selectedMeasurementId = measurementId,
            trendSelectionMethod = BodyWeightTrendSelectionMethod.MANUAL_SELECTED,
            isTrendUserOverride = true
        )

        userPreferencesRepository.setWeightLogLastPromptResetEpochDay(date.toEpochDay())

        return Result.Success(
            id = logId,
            measurementId = measurementId
        )
    }

    sealed interface Result {
        data class Success(
            val id: Long,
            val measurementId: Long
        ) : Result

        data class Error(val message: String) : Result
    }
}

/**
 * Uses the current clock time for today's manual entry, but assigns a stable
 * midday timestamp for backfilled/manual edits on another date.
 *
 * This keeps historical manual entries grouped on the intended local date while
 * avoiding accidental midnight ordering assumptions once AK later lets the user
 * choose a preferred weigh-in time for automatic trend selection.
 */
private fun manualMeasurementInstantFor(
    date: LocalDate,
    now: Instant,
    zoneId: ZoneId
): Instant {
    val today = LocalDate.now(zoneId)
    if (date == today) return now

    return date
        .atTime(BACKDATED_MANUAL_MEASUREMENT_TIME)
        .atZone(zoneId)
        .toInstant()
}

private fun Double.toKilograms(unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> this
        BodyWeightUnit.LB -> this / POUNDS_PER_KILOGRAM
    }

private const val POUNDS_PER_KILOGRAM = 2.2046226218
private val BACKDATED_MANUAL_MEASUREMENT_TIME: LocalTime = LocalTime.NOON

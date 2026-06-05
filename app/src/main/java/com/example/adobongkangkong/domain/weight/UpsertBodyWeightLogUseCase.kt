package com.example.adobongkangkong.domain.weight

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Saves one body-weight log for a date.
 *
 * MVP behavior:
 * - One body-weight log per date.
 * - Saving weight for an existing date updates that date's row.
 * - A successful save resets the dashboard weight-log reminder counter.
 *
 * Reminder rule:
 * - LOG_WEIGHT resets `weightLogLastPromptResetEpochDay` to the logged date's epoch day.
 * - This hides the dashboard ribbon until the configured interval elapses again.
 */
class UpsertBodyWeightLogUseCase @Inject constructor(
    private val repository: BodyWeightLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository
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

        val id = repository.upsertByDate(
            date = date,
            weight = weight,
            unit = unit,
            note = note
        )

        userPreferencesRepository.setWeightLogLastPromptResetEpochDay(date.toEpochDay())

        return Result.Success(id = id)
    }

    sealed interface Result {
        data class Success(val id: Long) : Result
        data class Error(val message: String) : Result
    }
}
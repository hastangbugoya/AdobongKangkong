package com.example.adobongkangkong.domain.weight

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.settings.WeightLogReminderMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dismisses the dashboard weight-log reminder ribbon when dismissal is allowed.
 *
 * Reminder behavior:
 * - NO_WARNING: no-op because no ribbon should be shown.
 * - REMINDER: dismissal is allowed and resets the N-day counter.
 * - REQUIRE: dismissal is not allowed; user must log weight to clear the ribbon.
 *
 * This use case does not touch body-weight log records.
 * It only updates the preference reset anchor used by the dashboard ribbon due logic.
 */
class DismissWeightLogReminderUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    operator fun invoke(
        today: LocalDate,
        mode: WeightLogReminderMode
    ): Result {
        return when (mode) {
            WeightLogReminderMode.NO_WARNING -> {
                Result.NoOp
            }

            WeightLogReminderMode.REMINDER -> {
                userPreferencesRepository.setWeightLogLastPromptResetEpochDay(
                    today.toEpochDay()
                )
                Result.Dismissed
            }

            WeightLogReminderMode.REQUIRE -> {
                Result.Blocked("Weight logging is required by your current setting.")
            }
        }
    }

    sealed interface Result {
        data object Dismissed : Result
        data object NoOp : Result
        data class Blocked(val message: String) : Result
    }
}
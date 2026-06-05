package com.example.adobongkangkong.domain.weight

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.settings.WeightLogReminderMode
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes whether the dashboard should show the body-weight logging ribbon.
 *
 * Reminder rules:
 * - NO_WARNING: never show the dashboard ribbon.
 * - REMINDER: show when due; user may dismiss.
 * - REQUIRE: show when due; user cannot dismiss and must log weight to clear it.
 *
 * Due logic:
 * - Counter is based on epoch-day difference.
 * - Counter resets when:
 *   - user dismisses the ribbon in REMINDER mode
 *   - user successfully logs weight
 *
 * Fallback:
 * - If the reset anchor is missing but weight logs exist, use the latest weight log date
 *   as the anchor. This keeps old/seeded weight logs from immediately prompting.
 * - If there is no reset anchor and no weight log, non-NO_WARNING modes show the ribbon.
 */
class ObserveWeightLogReminderRibbonUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val bodyWeightLogRepository: BodyWeightLogRepository
) {

    operator fun invoke(
        today: LocalDate
    ): Flow<WeightLogReminderRibbonState> {
        val todayEpochDay = today.toEpochDay()

        return combine(
            userPreferencesRepository.weightLogReminderMode,
            userPreferencesRepository.weightLogIntervalDays,
            userPreferencesRepository.weightLogLastPromptResetEpochDay,
            bodyWeightLogRepository.observeLatest()
        ) { mode, rawIntervalDays, resetEpochDay, latestWeightLog ->
            val intervalDays = rawIntervalDays.coerceAtLeast(1)

            if (mode == WeightLogReminderMode.NO_WARNING) {
                return@combine WeightLogReminderRibbonState.Hidden
            }

            val anchorEpochDay =
                resetEpochDay ?: latestWeightLog?.date?.toEpochDay()

            val daysSinceReset =
                anchorEpochDay?.let { todayEpochDay - it }

            val isDue =
                when {
                    anchorEpochDay == null -> true
                    daysSinceReset == null -> true
                    daysSinceReset >= intervalDays -> true
                    else -> false
                }

            if (!isDue) {
                return@combine WeightLogReminderRibbonState.Hidden
            }

            WeightLogReminderRibbonState.Visible(
                mode = mode,
                intervalDays = intervalDays,
                daysSinceReset = daysSinceReset?.coerceAtLeast(0),
                canDismiss = mode == WeightLogReminderMode.REMINDER,
                title = "Time to log weight",
                message = when (mode) {
                    WeightLogReminderMode.NO_WARNING -> ""
                    WeightLogReminderMode.REMINDER -> {
                        buildReminderMessage(
                            daysSinceReset = daysSinceReset,
                            intervalDays = intervalDays,
                            required = false
                        )
                    }

                    WeightLogReminderMode.REQUIRE -> {
                        buildReminderMessage(
                            daysSinceReset = daysSinceReset,
                            intervalDays = intervalDays,
                            required = true
                        )
                    }
                }
            )
        }
    }

    private fun buildReminderMessage(
        daysSinceReset: Long?,
        intervalDays: Int,
        required: Boolean
    ): String {
        val base = when {
            daysSinceReset == null -> {
                "Log your current weight to start tracking meal-plan effects."
            }

            daysSinceReset <= 0L -> {
                "Log your current weight to keep your trend up to date."
            }

            daysSinceReset == 1L -> {
                "It has been 1 day since your last weight-log reset."
            }

            else -> {
                "It has been $daysSinceReset days since your last weight-log reset."
            }
        }

        return if (required) {
            "$base This reminder will stay until weight is logged."
        } else {
            "$base You can log weight now or dismiss for another $intervalDays days."
        }
    }
}

/**
 * UI-ready dashboard state for the body-weight logging ribbon.
 *
 * This is intentionally separate from BodyWeightLog records.
 */
sealed interface WeightLogReminderRibbonState {

    data object Hidden : WeightLogReminderRibbonState

    data class Visible(
        val mode: WeightLogReminderMode,
        val intervalDays: Int,
        val daysSinceReset: Long?,
        val canDismiss: Boolean,
        val title: String,
        val message: String
    ) : WeightLogReminderRibbonState
}
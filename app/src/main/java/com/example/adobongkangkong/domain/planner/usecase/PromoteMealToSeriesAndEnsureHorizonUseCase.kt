package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import javax.inject.Inject

/**
 * Promotes a single planned meal occurrence into a recurring series and ensures
 * future occurrences exist within a bounded horizon window.
 *
 * (docs unchanged)
 */
class PromoteMealToSeriesAndEnsureHorizonUseCase @Inject constructor(
    private val promote: CreateSeriesFromPlannedMealUseCase,
    private val ensure: EnsureSeriesOccurrencesWithinHorizonUseCase,
) {
    suspend fun execute(
        mealId: Long,
        horizonDays: Long = 180,
        slotRulesOverride: List<CreatePlannedSeriesUseCase.SlotRuleInput>? = null,

        // 🔥 NEW
        endConditionType: PlannedSeriesEndConditionType = PlannedSeriesEndConditionType.INDEFINITE,
        endConditionValue: String? = null,
    ): Long {

        val result = promote.execute(
            mealId = mealId,
            slotRulesOverride = slotRulesOverride,

            // 🔥 PASS THROUGH
            endConditionType = endConditionType,
            endConditionValue = endConditionValue,
        )

        val startIso = result.anchorDate.toString()
        val endIso = result.anchorDate.plusDays(horizonDays).toString()

        ensure.execute(
            seriesId = result.seriesId,
            startDateIso = startIso,
            endDateIso = endIso,
        )

        return result.seriesId
    }
}
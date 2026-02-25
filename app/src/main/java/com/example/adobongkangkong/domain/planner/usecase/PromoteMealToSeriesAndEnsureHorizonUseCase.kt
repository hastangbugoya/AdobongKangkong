package com.example.adobongkangkong.domain.planner.usecase

import jakarta.inject.Inject

class PromoteMealToSeriesAndEnsureHorizonUseCase @Inject constructor(
    private val promote: CreateSeriesFromPlannedMealUseCase,
    private val ensure: EnsureSeriesOccurrencesWithinHorizonUseCase,
) {
    suspend fun execute(mealId: Long, horizonDays: Long = 180): Long {
        val result = promote.execute(mealId)
        val startIso = result.anchorDate.toString()
        val endIso = result.anchorDate.plusDays(horizonDays).toString()
        ensure.execute(seriesId = result.seriesId, startDateIso = startIso, endDateIso = endIso,)
        return result.seriesId
    }
}
package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedSeriesUseCase
import java.time.LocalDate
import javax.inject.Inject

class CreateSeriesAndEnsureHorizonUseCase @Inject constructor(
    private val createSeries: CreatePlannedSeriesUseCase,
    private val ensureSeries: EnsureSeriesOccurrencesWithinHorizonUseCase,
) {
    suspend fun execute(
        input: CreatePlannedSeriesUseCase.Input,
        anchorDate: LocalDate,
        horizonDays: Long = 180
    ): Long {
        val seriesId = createSeries.execute(input)
        val startIso = anchorDate.toString()
        val endIso = anchorDate.plusDays(horizonDays).toString()
        ensureSeries.execute(seriesId = seriesId, startDateIso = startIso, endDateIso = endIso)
        return seriesId
    }
}
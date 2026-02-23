package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

class DeletePlannedSeriesUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {
    suspend fun execute(seriesId: Long) {
        val series = repo.getSeriesById(seriesId) ?: return
        repo.deleteSeries(series)
    }
}
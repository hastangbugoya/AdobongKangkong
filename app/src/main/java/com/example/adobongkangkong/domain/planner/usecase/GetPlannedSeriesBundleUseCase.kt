package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

class GetPlannedSeriesBundleUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {
    data class Result(
        val series: PlannedSeriesEntity,
        val slotRules: List<PlannedSeriesSlotRuleEntity>
    )

    suspend fun execute(seriesId: Long): Result? {
        val series = repo.getSeriesById(seriesId) ?: return null
        val rules = repo.getSlotRulesForSeries(seriesId)
        return Result(series, rules)
    }
}
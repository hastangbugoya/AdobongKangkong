package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

class UpdatePlannedSeriesUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {

    suspend fun execute(
        updated: PlannedSeriesEntity,
        newRules: List<com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity>
    ) {
        val existing = repo.getSeriesById(updated.id)
            ?: error("Series not found: id=${updated.id}")

        val now = System.currentTimeMillis()

        repo.updateSeries(
            existing.copy(
                effectiveStartDate = updated.effectiveStartDate,
                effectiveEndDate = updated.effectiveEndDate,
                endConditionType = updated.endConditionType,
                endConditionValue = updated.endConditionValue,
                updatedAtEpochMs = now
            )
        )

        // Ensure rules point to the correct seriesId; keep createdAt unchanged if you want later.
        val rulesFixed = newRules.map { it.copy(seriesId = existing.id) }
        repo.replaceSlotRules(existing.id, rulesFixed)
    }
}
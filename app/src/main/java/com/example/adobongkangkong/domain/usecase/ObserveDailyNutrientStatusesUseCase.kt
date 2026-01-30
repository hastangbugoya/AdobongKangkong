package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutrientStatus
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveDailyNutrientStatusesUseCase @Inject constructor(
    private val observeTotals: ObserveDailyNutritionTotalsUseCase,
    private val targetsRepo: UserNutrientTargetRepository
) {
    operator fun invoke(date: LocalDate, zoneId: ZoneId): Flow<List<DailyNutrientStatus>> =
        combine(
            observeTotals(date, zoneId),
            targetsRepo.observeTargets()
        ) { totals, targets ->
            val result = mutableListOf<DailyNutrientStatus>()

            // Only show nutrients that have targets (logic first).
            for ((code, target) in targets) {
                val consumed = totals.totalsByCode[NutrientKey(code)] ?: 0.0
                result += DailyNutrientStatus(
                    nutrientCode = code,
                    consumed = consumed,
                    min = target.minPerDay,
                    target = target.targetPerDay,
                    max = target.maxPerDay,
                    status = computeStatus(consumed, target.minPerDay, target.maxPerDay)
                )
            }
            result
        }

    private fun computeStatus(consumed: Double, min: Double?, max: Double?): TargetStatus =
        when {
            min != null && consumed < min -> TargetStatus.LOW
            max != null && consumed > max -> TargetStatus.HIGH
            min == null && max == null -> TargetStatus.NO_TARGET
            else -> TargetStatus.OK
        }
}

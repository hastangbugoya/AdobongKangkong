package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes user-configured nutrient targets keyed by nutrient code.
 *
 * Returned map is keyed by nutrientCode for O(1) lookup during
 * dashboard aggregation and status evaluation.
 */
class ObserveDashboardTargetsUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    operator fun invoke(): Flow<Map<String, UserNutrientTarget>> =
        repo.observeTargets()
}

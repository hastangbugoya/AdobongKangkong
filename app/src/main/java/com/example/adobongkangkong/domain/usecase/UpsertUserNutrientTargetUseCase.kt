package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import javax.inject.Inject

class UpsertUserNutrientTargetUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    suspend operator fun invoke(
        nutrientCode: String,
        min: Double?,
        target: Double?,
        max: Double?
    ) {
        require(min == null || min >= 0)
        require(target == null || target >= 0)
        require(max == null || max >= 0)

        if (min != null && max != null) require(min <= max)

        repo.upsert(
            UserNutrientTarget(
                nutrientCode = nutrientCode,
                minPerDay = min,
                targetPerDay = target,
                maxPerDay = max
            )
        )
    }
}

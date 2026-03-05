package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannerIouEntity
import com.example.adobongkangkong.domain.repository.PlannerIouRepository
import javax.inject.Inject

class CreatePlannerIouUseCase @Inject constructor(
    private val ious: PlannerIouRepository
) {

    suspend operator fun invoke(
        dateIso: String,
        description: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Long {
        require(dateIso.isNotBlank()) { "dateIso must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }

        return ious.insert(
            PlannerIouEntity(
                id = 0L,
                dateIso = dateIso,
                description = description,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
    }
}

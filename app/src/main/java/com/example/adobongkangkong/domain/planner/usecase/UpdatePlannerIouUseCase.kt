package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannerIouRepository
import javax.inject.Inject

class UpdatePlannerIouUseCase @Inject constructor(
    private val ious: PlannerIouRepository
) {

    suspend operator fun invoke(
        iouId: Long,
        newDescription: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        require(iouId > 0L) { "iouId must be > 0" }
        require(newDescription.isNotBlank()) { "newDescription must not be blank" }

        val existing = ious.getById(iouId) ?: return

        ious.update(
            existing.copy(
                description = newDescription,
                updatedAtEpochMs = nowEpochMs
            )
        )
    }
}

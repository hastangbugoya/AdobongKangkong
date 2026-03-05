package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannerIouRepository
import javax.inject.Inject

class DeletePlannerIouUseCase @Inject constructor(
    private val ious: PlannerIouRepository
) {

    suspend operator fun invoke(iouId: Long) {
        if (iouId <= 0L) return
        ious.deleteById(iouId)
    }
}

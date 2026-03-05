package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.IouRepository
import javax.inject.Inject

class UpdateIouUseCase @Inject constructor(
    private val ious: IouRepository
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

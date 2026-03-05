package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.IouRepository
import javax.inject.Inject

class DeleteIouUseCase @Inject constructor(
    private val ious: IouRepository
) {

    suspend operator fun invoke(iouId: Long) {
        if (iouId <= 0L) return
        ious.deleteById(iouId)
    }
}

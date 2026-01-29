package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.TargetEdit
import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import javax.inject.Inject

class UpsertUserNutrientTargetUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    suspend operator fun invoke(edit: TargetEdit) {
        // validate consistency
        require(!(edit.min != null && edit.max != null && edit.min > edit.max)) {
            "min cannot be greater than max"
        }
        require(!(edit.target != null && edit.min != null && edit.target < edit.min)) {
            "target cannot be less than min"
        }
        require(!(edit.target != null && edit.max != null && edit.target > edit.max)) {
            "target cannot be greater than max"
        }

        repo.upsert(
            UserNutrientTarget(
                nutrientCode = edit.key.value,
                minPerDay = edit.min,
                targetPerDay = edit.target,
                maxPerDay = edit.max
            )
        )
    }
}

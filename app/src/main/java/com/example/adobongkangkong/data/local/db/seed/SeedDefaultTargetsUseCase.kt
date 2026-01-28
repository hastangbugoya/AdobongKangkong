package com.example.adobongkangkong.data.local.db.seed

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import javax.inject.Inject

class SeedDefaultTargetsUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    suspend operator fun invoke() {
        if (repo.hasAnyTargets()) return
        repo.upsertAll(
            listOf(
                UserNutrientTarget("calories_kcal", targetPerDay = 2000.0),
                UserNutrientTarget("protein_g", minPerDay = 120.0),
                UserNutrientTarget("carbs_g", targetPerDay = 200.0),
                UserNutrientTarget("fat_g", maxPerDay = 70.0),
                UserNutrientTarget("fiber_g", minPerDay = 30.0),
                UserNutrientTarget("sodium_mg", maxPerDay = 2300.0)
            )
        )
    }
}

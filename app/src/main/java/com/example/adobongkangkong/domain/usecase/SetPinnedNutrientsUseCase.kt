package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

class SetPinnedNutrientsUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(slot0: NutrientKey?, slot1: NutrientKey?) {
        require(slot0 == null || slot0 != slot1) { "Pinned nutrients must be distinct" }
        repo.setPinnedPositions(slot0, slot1)
    }
}

package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

class SetPinnedNutrientUseCase @Inject constructor(
    private val pinnedRepo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(position: Int, key: NutrientKey?) {
        require(position == 0 || position == 1) { "position must be 0 or 1" }
        pinnedRepo.setPinned(position, key)
    }
}

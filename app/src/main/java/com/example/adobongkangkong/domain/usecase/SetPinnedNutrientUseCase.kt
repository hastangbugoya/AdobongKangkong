package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

class SetPinnedNutrientUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(position: Int, key: NutrientKey?) {

        require(position == 0 || position == 1) { "position must be 0 or 1" }

        repo.setPinned(position = position, key = key)
    }
}
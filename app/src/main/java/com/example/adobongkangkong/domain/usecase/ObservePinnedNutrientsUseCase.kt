package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePinnedNutrientsUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    operator fun invoke(): Flow<List<NutrientKey>> = repo.observePinnedKeys()
}

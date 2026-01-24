package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchNutrientsUseCase @Inject constructor(
    private val repo: NutrientRepository
) {
    operator fun invoke(query: String, limit: Int = 50): Flow<List<Nutrient>> =
        repo.search(query, limit)
}

package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import javax.inject.Inject

class SearchNutrientsUseCase @Inject constructor(
    private val repo: NutrientRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 50): List<Nutrient> =
        repo.search(query, limit)
}

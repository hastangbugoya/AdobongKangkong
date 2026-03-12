package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.NutrientCatalogBootstrapRepository
import javax.inject.Inject

class EnsureNutrientCatalogSeededUseCase @Inject constructor(
    private val repo: NutrientCatalogBootstrapRepository
) {
    suspend operator fun invoke() {
        repo.seedCsvCatalogIfMissing()
    }
}
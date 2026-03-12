package com.example.adobongkangkong.domain.repository

interface NutrientCatalogBootstrapRepository {
    suspend fun nutrientCount(): Int
    suspend fun seedCsvCatalogIfMissing()
}
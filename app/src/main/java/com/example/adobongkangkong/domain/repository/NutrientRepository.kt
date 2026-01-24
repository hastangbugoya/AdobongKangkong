package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.Nutrient

interface NutrientRepository {
    suspend fun search(query: String, limit: Int = 50): List<Nutrient>
}
package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.Nutrient
import kotlinx.coroutines.flow.Flow


interface NutrientRepository {
    fun search(query: String, limit: Int = 50): Flow<List<Nutrient>>

    fun observeAllNutrients(): Flow<List<Nutrient>>

    suspend fun getByCode(code: String): Nutrient?
}
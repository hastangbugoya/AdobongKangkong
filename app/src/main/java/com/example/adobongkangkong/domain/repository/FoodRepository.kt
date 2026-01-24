package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.Food
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun search(query: String, limit: Int = 50): Flow<List<Food>>

    suspend fun getById(id: Long): Food?

    suspend fun upsert(food: Food): Long
}


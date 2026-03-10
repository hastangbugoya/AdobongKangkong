package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.FoodCategory

interface FoodCategoryRepository {
    suspend fun getAll(): List<FoodCategory>

    suspend fun getForFood(foodId: Long): List<FoodCategory>

    suspend fun getOrCreateByName(name: String): FoodCategory

    suspend fun replaceForFood(foodId: Long, categoryIds: Set<Long>)
}

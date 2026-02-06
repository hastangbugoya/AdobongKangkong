package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun search(query: String, limit: Int = 50): Flow<List<Food>>

    suspend fun getById(id: Long): Food?

    suspend fun upsert(food: Food): Long

    suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food?

    suspend fun isFoodsEmpty(): Boolean

    /**
     * Deletes a food and all food-owned data (nutrients, goal flags).
     *
     * @return true if deleted, false if blocked (e.g., food is referenced by a recipe).
     */
    suspend fun deleteFood(foodId: Long): Boolean
}



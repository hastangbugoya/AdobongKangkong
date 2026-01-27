package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.FoodGoalFlags
import kotlinx.coroutines.flow.Flow

interface FoodGoalFlagsRepository {
    suspend fun get(foodId: Long): FoodGoalFlags?
    fun observe(foodId: Long): Flow<FoodGoalFlags?>
    suspend fun setFlags(foodId: Long, eatMore: Boolean, limit: Boolean, favorite: Boolean)
    suspend fun clear(foodId: Long)
}

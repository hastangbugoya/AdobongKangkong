package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import kotlinx.coroutines.flow.Flow

interface PlannedItemRepository {

    fun observeItemsForMeal(mealId: Long): Flow<List<PlannedItemEntity>>

    suspend fun getById(id: Long): PlannedItemEntity?

    suspend fun getItemsForMeal(mealId: Long): List<PlannedItemEntity>

    suspend fun insert(entity: PlannedItemEntity): Long

    suspend fun update(entity: PlannedItemEntity)

    suspend fun delete(entity: PlannedItemEntity)

    suspend fun deleteById(itemId: Long)

    suspend fun deleteForMeal(mealId: Long)

    suspend fun getMaxSortOrderForMeal(mealId: Long): Int

}
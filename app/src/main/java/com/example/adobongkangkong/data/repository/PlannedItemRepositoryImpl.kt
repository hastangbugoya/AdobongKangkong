package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannedItemDao
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlannedItemRepositoryImpl @Inject constructor(
    private val dao: PlannedItemDao
) : PlannedItemRepository {

    override fun observeItemsForMeal(mealId: Long): Flow<List<PlannedItemEntity>> =
        dao.observeItemsForMeal(mealId)

    override suspend fun getById(id: Long): PlannedItemEntity? =
        dao.getById(id)

    override suspend fun getItemsForMeal(mealId: Long): List<PlannedItemEntity> =
        dao.getItemsForMeal(mealId)

    override suspend fun insert(entity: PlannedItemEntity): Long =
        dao.insert(entity)

    override suspend fun update(entity: PlannedItemEntity) =
        dao.update(entity)

    override suspend fun delete(entity: PlannedItemEntity) =
        dao.delete(entity)

    override suspend fun deleteById(itemId: Long) =
        dao.deleteItemById(itemId)

    override suspend fun deleteForMeal(mealId: Long) =
        dao.deleteItemsForMeal(mealId)

    override suspend fun getMaxSortOrderForMeal(mealId: Long): Int =
        dao.getMaxSortOrderForMeal(mealId)
}

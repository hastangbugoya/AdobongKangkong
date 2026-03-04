package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RecipeBatchLookupRepositoryImpl @Inject constructor(
    private val recipeBatchDao: RecipeBatchDao
) : RecipeBatchLookupRepository {

    override suspend fun getBatchById(batchId: Long): BatchSummary? {
        val entity = recipeBatchDao.getById(batchId) ?: return null
        return entity.toDomain()
    }

    override suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary> {
        return recipeBatchDao.observeBatchesForRecipe(recipeId)
            .first()
            .map { it.toDomain() }
    }

    override suspend fun getBatchFoodId(batchId: Long): Long? {
        return recipeBatchDao.getById(batchId)?.batchFoodId
    }

    override suspend fun getBatchFoodIds(batchIds: Set<Long>): Map<Long, Long> {
        if (batchIds.isEmpty()) return emptyMap()
        return recipeBatchDao.getByIds(batchIds.toList())
            .associate { it.id to it.batchFoodId }
    }
}

package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
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
}

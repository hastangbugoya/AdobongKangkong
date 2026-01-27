package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import java.time.Instant
import javax.inject.Inject

class CreateRecipeBatchUseCase @Inject constructor(
    private val recipeBatchDao: RecipeBatchDao
) {
    suspend operator fun invoke(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double? = null,
        createdAt: Instant = Instant.now()
    ): Long {
        require(cookedYieldGrams > 0.0) { "Cooked yield must be > 0g" }

        val entity = RecipeBatchEntity(
            id = 0L,
            recipeId = recipeId,
            cookedYieldGrams = cookedYieldGrams,
            servingsYieldUsed = servingsYieldUsed,
            createdAt = createdAt
        )
        return recipeBatchDao.insert(entity)
    }
}

package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.recipes.CreateSnapshotFoodFromRecipeUseCase
import java.time.Instant
import javax.inject.Inject

class CreateRecipeBatchUseCase @Inject constructor(
    private val recipeBatchDao: RecipeBatchDao,
    private val createBatchFoodFromRecipeUseCase: CreateSnapshotFoodFromRecipeUseCase
) {
    suspend operator fun invoke(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double? = null,
        createdAt: Instant = Instant.now()
    ): Long {
        require(cookedYieldGrams > 0.0) { "Cooked yield must be > 0g" }

        // 1️⃣ Create snapshot food
        val result = createBatchFoodFromRecipeUseCase.execute(
            recipeId = recipeId,
            cookedYieldGrams = cookedYieldGrams,
            servingsYieldUsed = servingsYieldUsed
        )

        val batchFoodId = result.batchFoodId

        // 2️⃣ Create batch referencing snapshot food
        val entity = RecipeBatchEntity(
            id = 0L,
            recipeId = recipeId,
            batchFoodId = batchFoodId,
            cookedYieldGrams = cookedYieldGrams,
            servingsYieldUsed = servingsYieldUsed,
            createdAt = createdAt
        )

        return recipeBatchDao.insert(entity)
    }
}

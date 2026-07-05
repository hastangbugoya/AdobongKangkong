package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.dao.RecipeMeasuredYieldDao
import com.example.adobongkangkong.data.local.db.entity.RecipeMeasuredYieldEntity
import javax.inject.Inject

class GetActiveRecipeMeasuredYieldUseCase @Inject constructor(
    private val dao: RecipeMeasuredYieldDao
) {
    suspend fun execute(
        recipeId: Long,
        variantId: Long?
    ): RecipeMeasuredYieldEntity? {
        return dao.getActiveYield(
            recipeId = recipeId,
            variantId = variantId
        )
    }
}
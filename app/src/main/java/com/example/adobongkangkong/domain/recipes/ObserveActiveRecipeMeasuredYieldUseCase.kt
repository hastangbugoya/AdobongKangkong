package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.dao.RecipeMeasuredYieldDao
import com.example.adobongkangkong.data.local.db.entity.RecipeMeasuredYieldEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveActiveRecipeMeasuredYieldUseCase @Inject constructor(
    private val dao: RecipeMeasuredYieldDao
) {
    fun execute(
        recipeId: Long,
        variantId: Long?
    ): Flow<RecipeMeasuredYieldEntity?> {
        return dao.observeActiveYield(
            recipeId = recipeId,
            variantId = variantId
        )
    }
}
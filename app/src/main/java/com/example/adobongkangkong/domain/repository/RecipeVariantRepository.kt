package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import kotlinx.coroutines.flow.Flow

interface RecipeVariantRepository {

    fun observeVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    fun observeActiveVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    suspend fun createVariant(
        recipeFoodId: Long,
        name: String,
        notes: String?,
        nowEpochMillis: Long,
    ): Long

    suspend fun archiveVariant(
        variantId: Long,
        nowEpochMillis: Long,
    )

    suspend fun restoreVariant(
        variantId: Long,
        nowEpochMillis: Long,
    )
}
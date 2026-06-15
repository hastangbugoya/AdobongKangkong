package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import kotlinx.coroutines.flow.Flow

interface RecipeVariantRepository {

    fun observeVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    fun observeActiveVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    suspend fun getVariantById(
        variantId: Long,
    ): RecipeVariantEntity?

    suspend fun createVariant(
        recipeFoodId: Long,
        name: String,
        notes: String?,
        nowEpochMillis: Long,
    ): Long

    suspend fun updateVariant(
        variant: RecipeVariantEntity,
    )

    suspend fun archiveVariant(
        variantId: Long,
        nowEpochMillis: Long,
    )

    suspend fun restoreVariant(
        variantId: Long,
        nowEpochMillis: Long,
    )

    suspend fun deleteArchivedVariant(
        variantId: Long,
    )

    suspend fun getChangesForVariant(
        variantId: Long,
    ): List<RecipeVariantIngredientChangeEntity>

    suspend fun replaceChangesForVariant(
        variantId: Long,
        changes: List<RecipeVariantIngredientChangeEntity>,
    )

    suspend fun updateVariantNutritionSnapshot(
        variantId: Long,
        nutrientsJsonSnapshot: String?,
        nowEpochMillis: Long,
    )
}

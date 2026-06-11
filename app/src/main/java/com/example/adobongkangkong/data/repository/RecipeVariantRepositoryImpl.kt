package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.RecipeVariantDao
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeVariantRepositoryImpl @Inject constructor(
    private val recipeVariantDao: RecipeVariantDao,
) : RecipeVariantRepository {

    override fun observeVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>> {
        return recipeVariantDao.observeVariantsForRecipe(recipeFoodId)
    }

    override fun observeActiveVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>> {
        return recipeVariantDao.observeActiveVariantsForRecipe(recipeFoodId)
    }

    override suspend fun createVariant(
        recipeFoodId: Long,
        name: String,
        notes: String?,
        nowEpochMillis: Long,
    ): Long {
        return recipeVariantDao.insertVariant(
            RecipeVariantEntity(
                recipeFoodId = recipeFoodId,
                name = name.trim(),
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                isArchived = false,
                servingsYieldOverride = null,
                totalYieldGramsOverride = null,
                nutrientsJsonSnapshot = null,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
        )
    }

    override suspend fun archiveVariant(
        variantId: Long,
        nowEpochMillis: Long,
    ) {
        recipeVariantDao.archiveVariant(
            variantId = variantId,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }

    override suspend fun restoreVariant(
        variantId: Long,
        nowEpochMillis: Long,
    ) {
        recipeVariantDao.restoreVariant(
            variantId = variantId,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}